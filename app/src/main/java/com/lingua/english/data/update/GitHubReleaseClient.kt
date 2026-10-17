package com.lingua.english.data.update

import java.io.File
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

@Serializable
data class AppUpdateManifest(
    val applicationId: String,
    val versionCode: Int,
    val versionName: String,
    val apkName: String,
    val apkSha256: String,
    val signingCertificateSha256: String,
)

data class GitHubUpdate(val manifest: AppUpdateManifest, val assetId: Long, val bytes: Long)

@Serializable
private data class GitHubRelease(
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
private data class GitHubAsset(val id: Long, val name: String, val size: Long = 0)

/** Public releases and their assets are read without account credentials. */
class GitHubReleaseClient(
    private val apiBase: HttpUrl = "https://api.github.com/".toHttpUrl(),
    client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.MINUTES)
        .build(),
) {
    private val http = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private val json = Json { ignoreUnknownKeys = true }

    fun check(currentVersion: Int, applicationId: String): GitHubUpdate? {
        val release = apiRequest("releases/latest", "application/vnd.github+json").use { response ->
            checkResponse(response)
            json.decodeFromString<GitHubRelease>(readSmallBody(response, 1024 * 1024))
        }
        require(!release.draft && !release.prerelease) { "目前没有可安装的正式更新" }
        val info = release.assets.singleOrNull { it.name == "build-info.json" }
            ?: throw IOException("这个 GitHub 版本缺少更新信息，请重新运行 Android APK 工作流")
        val manifest = assetRequest(info.id).use { response ->
            json.decodeFromString<AppUpdateManifest>(readSmallBody(response, 64 * 1024))
        }
        validateManifest(manifest, applicationId)
        if (manifest.versionCode <= currentVersion) return null
        val apk = release.assets.singleOrNull { it.name == manifest.apkName }
            ?: throw IOException("这个 GitHub 版本没有对应的 APK")
        require(apk.id > 0 && apk.size in 1..MAX_APK_BYTES) { "更新安装包大小异常" }
        return GitHubUpdate(manifest, apk.id, apk.size)
    }

    fun download(update: GitHubUpdate, destination: File, onProgress: (Long) -> Unit) {
        require(update.bytes in 1..MAX_APK_BYTES) { "更新安装包大小异常" }
        val digest = MessageDigest.getInstance("SHA-256")
        assetRequest(update.assetId).use { response ->
            val body = response.body ?: throw IOException("GitHub 返回了空安装包")
            body.byteStream().use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var received = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count == -1) break
                        received += count
                        require(received <= update.bytes && received <= MAX_APK_BYTES) { "下载的安装包大小异常" }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        onProgress(received)
                    }
                    require(received == update.bytes) { "安装包未下载完整，请重试" }
                }
            }
        }
        require(digest.digest().toHex() == update.manifest.apkSha256.lowercase()) {
            "安装包校验未通过，请重新下载"
        }
    }

    private fun apiRequest(path: String, accept: String): Response {
        val url = apiBase.resolve("repos/$REPOSITORY/$path") ?: error("无效的 GitHub API 地址")
        return http.newCall(Request.Builder().url(url)
            .header("Accept", accept)
            .header("X-GitHub-Api-Version", "2026-03-10")
            .header("Cache-Control", "no-cache")
            .build()).execute()
    }

    private fun assetRequest(id: Long): Response {
        require(id > 0) { "GitHub 安装包地址无效" }
        var response = apiRequest("releases/assets/$id", "application/octet-stream")
        repeat(4) {
            if (response.code !in REDIRECT_CODES) {
                try {
                    checkResponse(response)
                    return response
                } catch (error: Exception) {
                    response.close()
                    throw error
                }
            }
            val next = response.header("Location")?.let { response.request.url.resolve(it) }
            response.close()
            require(next != null && isTrustedAssetUrl(next)) { "GitHub 下载地址异常，已停止下载" }
            response = http.newCall(Request.Builder().url(next).build()).execute()
        }
        response.close()
        throw IOException("GitHub 下载跳转过多，请重试")
    }

    private fun checkResponse(response: Response) {
        if (response.isSuccessful) return
        throw IOException(when (response.code) {
            401 -> "GitHub 暂时无法提供公开更新，请稍后重试"
            403 -> if (response.header("X-RateLimit-Remaining") == "0")
                "GitHub 请求暂时达到限额，请稍后再试"
            else "GitHub 暂时拒绝了更新请求，请稍后重试"
            404 -> "暂未找到可下载的公开版本，请稍后重试"
            else -> "GitHub 请求失败 (HTTP ${response.code})"
        })
    }

    private fun readSmallBody(response: Response, limit: Int): String {
        val body = response.body ?: throw IOException("GitHub 返回了空数据")
        val bytes = body.byteStream().use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                require(output.size() + count <= limit) { "GitHub 更新信息过大" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        return bytes.toString(Charsets.UTF_8)
    }

    companion object {
        const val REPOSITORY = "charlotteamian/Lingua"
        const val MAX_APK_BYTES = 200L * 1024 * 1024
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

        fun validateManifest(manifest: AppUpdateManifest, applicationId: String) {
            require(manifest.applicationId == applicationId) { "这个更新与当前 Lingua 的应用包名不一致" }
            require(manifest.versionCode > 0 && manifest.versionName.isNotBlank()) { "更新版本信息无效" }
            require(Regex("Lingua-build-[0-9]+\\.apk").matches(manifest.apkName)) { "更新安装包名称无效" }
            require(Regex("[0-9a-fA-F]{64}").matches(manifest.apkSha256) &&
                Regex("[0-9a-fA-F]{64}").matches(manifest.signingCertificateSha256)) { "更新校验信息缺失" }
        }

        fun isTrustedAssetUrl(url: HttpUrl): Boolean = url.isHttps &&
            url.host in setOf("release-assets.githubusercontent.com", "objects.githubusercontent.com")
    }
}

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
