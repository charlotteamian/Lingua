package com.lingua.english.data.update

import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GitHubReleaseClientTest {
    @get:Rule val server = MockWebServer()
    private val client get() = GitHubReleaseClient(server.url("/"))
    private val apk = "sample APK bytes".toByteArray()
    private val manifest = AppUpdateManifest(
        applicationId = "com.lingua.english.debug", versionCode = 1008,
        versionName = "0.1.0-build.8-debug", apkName = "Lingua-build-8.apk",
        apkSha256 = MessageDigest.getInstance("SHA-256").digest(apk).toHex(),
        signingCertificateSha256 = "a".repeat(64),
    )

    private fun enqueueRelease(info: AppUpdateManifest = manifest) {
        server.enqueue(MockResponse().setBody("""{"assets":[{"id":7,"name":"build-info.json","size":400},{"id":9,"name":"Lingua-build-8.apk","size":${apk.size}}]}"""))
        server.enqueue(MockResponse().setBody(Json.encodeToString(info)))
    }

    @Test fun readsPublicReleaseWithoutCredentialsAndSelectsNewerVersion() {
        enqueueRelease()
        val update = client.check(1001, manifest.applicationId)!!
        assertEquals(1008, update.manifest.versionCode)
        assertEquals(9L, update.assetId)
        val latest = server.takeRequest()
        assertEquals("/repos/charlotteamian/Lingua/releases/latest", latest.path)
        assertNull(latest.getHeader("Authorization"))
        assertNull(latest.getHeader("Cookie"))
        val asset = server.takeRequest()
        assertEquals("/repos/charlotteamian/Lingua/releases/assets/7", asset.path)
        assertEquals("application/octet-stream", asset.getHeader("Accept"))
        assertNull(asset.getHeader("Authorization"))
        assertNull(asset.getHeader("Cookie"))
    }

    @Test fun doesNotOfferEqualOrOlderVersion() {
        for (installed in listOf(1008, 1009)) {
            enqueueRelease()
            assertNull(client.check(installed, manifest.applicationId))
        }
    }

    @Test fun rejectsOtherApplicationPackage() {
        enqueueRelease(manifest.copy(applicationId = "com.other.app"))
        assertThrows(IllegalArgumentException::class.java) { client.check(1, manifest.applicationId) }
    }

    @Test fun rejectsPathInApkName() {
        enqueueRelease(manifest.copy(apkName = "../Lingua-build-8.apk"))
        assertThrows(IllegalArgumentException::class.java) { client.check(1, manifest.applicationId) }
    }

    @Test fun refusesReleaseWithoutIntegrityDigest() {
        enqueueRelease(manifest.copy(apkSha256 = ""))
        assertThrows(IllegalArgumentException::class.java) { client.check(1, manifest.applicationId) }
    }

    @Test fun doesNotAskForCredentialsWhenPublicAccessFails() {
        server.enqueue(MockResponse().setResponseCode(401))
        val error = assertThrows(IOException::class.java) { client.check(1, manifest.applicationId) }
        assertTrue(error.message!!.contains("公开更新"))
        assertFalse(error.message!!.contains("Token"))
    }

    @Test fun explainsMissingPublicRelease() {
        server.enqueue(MockResponse().setResponseCode(404))
        val error = assertThrows(IOException::class.java) { client.check(1, manifest.applicationId) }
        assertTrue(error.message!!.contains("公开版本"))
    }

    @Test fun refusesUnexpectedRedirectBeforeContactingAnotherHost() {
        server.enqueue(MockResponse().setBody("""{"assets":[{"id":7,"name":"build-info.json"}]}"""))
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://untrusted.example/steal"))
        assertThrows(IllegalArgumentException::class.java) { client.check(1, manifest.applicationId) }
        assertEquals(2, server.requestCount)
    }

    @Test fun permitsOnlyHttpsGitHubAssetHosts() {
        assertTrue(GitHubReleaseClient.isTrustedAssetUrl("https://release-assets.githubusercontent.com/file".toHttpUrl()))
        assertFalse(GitHubReleaseClient.isTrustedAssetUrl("http://release-assets.githubusercontent.com/file".toHttpUrl()))
        assertFalse(GitHubReleaseClient.isTrustedAssetUrl("https://release-assets.githubusercontent.com.evil.example/file".toHttpUrl()))
    }

    @Test fun followsTrustedAssetRedirectWithoutCredentials() {
        val requests = mutableListOf<Request>()
        val transport = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests += request
            val response = Response.Builder().request(request).protocol(Protocol.HTTP_1_1).message("test")
            when {
                request.url.encodedPath.endsWith("/latest") -> response.code(200).body(
                    """{"assets":[{"id":7,"name":"build-info.json"},{"id":9,"name":"Lingua-build-8.apk","size":${apk.size}}]}""".toResponseBody(),
                )
                request.url.host == "api.github.com" -> response.code(302)
                    .header("Location", "https://release-assets.githubusercontent.com/signed-manifest")
                    .body("".toResponseBody())
                else -> response.code(200).body(Json.encodeToString(manifest).toResponseBody())
            }.build()
        }.build()
        assertNotNull(GitHubReleaseClient(client = transport).check(1, manifest.applicationId))
        assertTrue(requests.all { it.header("Authorization") == null && it.header("Cookie") == null })
        assertEquals("release-assets.githubusercontent.com", requests[2].url.host)
    }

    @Test fun verifiesStreamedApkAndReportsProgress() {
        server.enqueue(MockResponse().setBody(String(apk)))
        val file = Files.createTempFile("lingua-download-", ".apk").toFile()
        try {
            var received = 0L
            client.download(GitHubUpdate(manifest, 9, apk.size.toLong()), file) { received = it }
            assertArrayEquals(apk, file.readBytes())
            assertEquals(apk.size.toLong(), received)
            val request = server.takeRequest()
            assertNull(request.getHeader("Authorization"))
            assertNull(request.getHeader("Cookie"))
        } finally { file.delete() }
    }

    @Test fun rejectsModifiedApk() {
        server.enqueue(MockResponse().setBody(String(apk)))
        val file = Files.createTempFile("lingua-download-", ".apk").toFile()
        try {
            assertThrows(IllegalArgumentException::class.java) {
                client.download(GitHubUpdate(manifest.copy(apkSha256 = "0".repeat(64)), 9, apk.size.toLong()), file) {}
            }
        } finally { file.delete() }
    }

    @Test fun rejectsTruncatedApk() {
        server.enqueue(MockResponse().setBody("short"))
        val file = Files.createTempFile("lingua-download-", ".apk").toFile()
        try {
            assertThrows(IllegalArgumentException::class.java) {
                client.download(GitHubUpdate(manifest, 9, apk.size.toLong()), file) {}
            }
        } finally { file.delete() }
    }

    @Test fun explainsAnonymousRateLimit() {
        server.enqueue(MockResponse().setResponseCode(403).setHeader("X-RateLimit-Remaining", "0"))
        val error = assertThrows(IOException::class.java) { client.check(1, manifest.applicationId) }
        assertTrue(error.message!!.contains("限额"))
        assertFalse(error.message!!.contains("Token"))
    }
}
