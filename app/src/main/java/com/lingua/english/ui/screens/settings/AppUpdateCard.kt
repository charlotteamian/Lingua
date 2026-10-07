package com.lingua.english.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lingua.english.BuildConfig
import com.lingua.english.ui.components.LinguaCard
import com.lingua.english.ui.theme.LinguaOnSurfaceMuted
import com.lingua.english.ui.theme.LinguaTertiary

@Composable
internal fun AppUpdateCard(vm: SettingsViewModel) {
    val context = LocalContext.current
    val token by vm.githubToken.collectAsStateWithLifecycle()
    val tokenLoaded by vm.githubTokenLoaded.collectAsStateWithLifecycle()
    val update by vm.appUpdate.collectAsStateWithLifecycle()
    val installer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.appInstallMessage("安装已交给系统；若未完成，可点下面的按钮继续安装。")
    }

    fun openInstaller() {
        val file = vm.appUpdate.value.apk ?: return
        try {
            check(file.isFile) { "下载的更新已被清理，请重新下载" }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
            installer.launch(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (error: Exception) {
            vm.appInstallMessage(error.message ?: "无法打开系统安装器，请重新下载")
        }
    }

    val installPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (context.packageManager.canRequestPackageInstalls()) openInstaller()
        else vm.appInstallMessage("需要在系统中允许 Lingua 安装应用，然后再点「安装已下载版本」。")
    }

    fun requestInstall() {
        try {
            if (context.packageManager.canRequestPackageInstalls()) openInstaller()
            else {
                vm.appInstallMessage("首次更新，请在系统中允许 Lingua 安装应用。")
                installPermission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
            }
        } catch (_: Exception) {
            vm.appInstallMessage("无法打开安装权限设置，请在系统设置中允许 Lingua 安装应用后再试。")
        }
    }

    LaunchedEffect(update.installationPending) {
        if (update.installationPending && update.apk != null) {
            vm.consumeAppInstallRequest()
            requestInstall()
        }
    }

    LinguaCard {
        Text("应用更新", style = MaterialTheme.typography.titleMedium)
        Text("当前版本 ${BuildConfig.VERSION_NAME}", color = LinguaOnSurfaceMuted)
        Text("从你的 GitHub 私有仓库获取新版本，覆盖安装保留学习记录。", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = token,
            onValueChange = vm::setGitHubToken,
            label = { Text("GitHub 只读 Token") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            enabled = tokenLoaded && !update.busy,
        )
        Text(
            "创建时只选择 Lingua 仓库，Contents 设为 Read-only。Token 在本机加密保存。",
            color = LinguaOnSurfaceMuted, style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                    "https://github.com/settings/personal-access-tokens/new?name=Lingua%20updates&target_name=charlotteamian&contents=read&expires_in=365",
                )))
            }.onFailure { vm.appInstallMessage("无法打开浏览器，请在 GitHub 设置中创建只读 Token") }
        }) { Text("在 GitHub 创建只读 Token") }

        when (update.stage) {
            UpdateStage.CHECKING -> Text("正在检查新版本…")
            UpdateStage.CURRENT -> Text("当前已是最新版本", color = LinguaTertiary)
            UpdateStage.AVAILABLE -> Text("发现新版本 ${update.release?.manifest?.versionName}", color = LinguaTertiary)
            UpdateStage.DOWNLOADING -> {
                Text("正在下载 ${update.progress}%")
                LinearProgressIndicator(progress = { update.progress / 100f }, modifier = Modifier.fillMaxWidth())
            }
            UpdateStage.READY -> Text("安装包已下载并通过校验", color = LinguaTertiary)
            else -> Unit
        }
        update.message?.let {
            Text(it, color = if (update.stage == UpdateStage.ERROR) MaterialTheme.colorScheme.error else LinguaOnSurfaceMuted,
                style = MaterialTheme.typography.bodyMedium)
        }
        Button(onClick = vm::checkAppUpdate, enabled = tokenLoaded && token.isNotBlank() && !update.busy,
            modifier = Modifier.fillMaxWidth()) { Text("检查更新") }
        if (update.release != null && !update.busy) {
            Button(onClick = { if (update.apk != null) requestInstall() else vm.downloadAppUpdate() },
                modifier = Modifier.fillMaxWidth()) {
                Text(if (update.apk != null) "安装已下载版本" else "下载并安装新版本")
            }
        }
    }
}
