package com.lingua.english.data.update

import android.content.Context
import android.content.pm.PackageManager
import com.lingua.english.BuildConfig
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class AppUpdateRepository(
    private val context: Context,
    private val client: GitHubReleaseClient = GitHubReleaseClient(),
) {
    val tokenStore = GitHubTokenStore(context)

    suspend fun check(token: String): GitHubUpdate? = withContext(Dispatchers.IO) {
        client.check(token, BuildConfig.VERSION_CODE, context.packageName)?.also { update ->
            require(currentCertificates().contains(update.manifest.signingCertificateSha256.lowercase())) {
                "GitHub 新版本的签名与当前 App 不一致，无法保留数据覆盖更新"
            }
        }
    }

    suspend fun download(update: GitHubUpdate, token: String, onProgress: (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, "updates").apply { mkdirs() }
            // Keep the file name under our control, rather than using a server-supplied path.
            val part = File(directory, "update.partial.apk")
            val target = File(directory, "Lingua-${update.manifest.versionCode}.apk")
            val coroutine = currentCoroutineContext()
            try {
                client.download(update, token, part) { received ->
                    coroutine.ensureActive()
                    onProgress((received * 100 / update.bytes).toInt().coerceIn(0, 100))
                }
                coroutine.ensureActive()
                verifyPackage(part, update)
                target.delete()
                check(part.renameTo(target)) { "无法保存下载的更新，请重试" }
                target
            } finally {
                part.delete()
            }
        }

    @Suppress("DEPRECATION")
    private fun verifyPackage(file: File, update: GitHubUpdate) {
        val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: error("安装包无法解析，请重新下载")
        require(info.packageName == context.packageName && info.longVersionCode == update.manifest.versionCode.toLong() &&
            info.longVersionCode > BuildConfig.VERSION_CODE) { "下载的安装包与更新版本不一致" }
        val certificates = info.signingInfo?.apkContentsSigners.orEmpty()
            .map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).toHex() }.toSet()
        require(certificates.isNotEmpty() && certificates == currentCertificates()) {
            "安装包签名与当前 App 不一致，已停止安装"
        }
    }

    @Suppress("DEPRECATION")
    private fun currentCertificates(): Set<String> = context.packageManager
        .getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        .signingInfo?.apkContentsSigners.orEmpty()
        .map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).toHex() }.toSet()
}
