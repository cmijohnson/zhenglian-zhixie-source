package com.eagleslab.yingge.data

import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/** Opens only Android's user-confirmed package installer for an already verified APK. */
internal class AppUpdateInstaller(private val context: Context) {
    enum class LaunchResult { INSTALLER_OPENED, PERMISSION_REQUIRED }

    fun launch(apk: File): LaunchResult {
        val verifiedDirectory = File(context.filesDir, "verified-updates").canonicalFile
        val candidate = apk.canonicalFile
        if (!candidate.isFile || candidate.parentFile != verifiedDirectory || candidate.extension != "apk") {
            throw RepositoryException("UPDATE_FILE_INVALID", "待安装更新包不在受控目录")
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                return LaunchResult.PERMISSION_REQUIRED
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", candidate)
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            return LaunchResult.INSTALLER_OPENED
        } catch (_: ActivityNotFoundException) {
            throw RepositoryException("UPDATE_INSTALLER_MISSING", "系统未找到可用的安装程序")
        } catch (_: SecurityException) {
            throw RepositoryException("UPDATE_INSTALLER_FORBIDDEN", "系统拒绝打开安装程序，请允许英格云安装未知应用")
        }
    }
}
