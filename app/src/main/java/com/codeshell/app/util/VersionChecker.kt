package com.codeshell.app.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.codeshell.app.data.model.AppConfig

enum class VersionCheckResult {
    Ok,
    SoftUpdate,
    ForceUpdate,
    Discontinued
}

object VersionChecker {

    fun currentVersionCode(context: Context): Long {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }

    fun evaluate(context: Context, config: AppConfig): VersionCheckResult {
        if (config.discontinued) return VersionCheckResult.Discontinued
        val current = currentVersionCode(context)
        if (current < config.minVersionCode) return VersionCheckResult.ForceUpdate
        if (current < config.latestVersionCode) return VersionCheckResult.SoftUpdate
        return VersionCheckResult.Ok
    }
}
