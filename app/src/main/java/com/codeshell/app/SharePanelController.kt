package com.codeshell.app

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Controls whether the app is exposed as a target in the system share panel.
 *
 * The share alias is disabled by default in the manifest so the disguise shell
 * never casually appears as a share target. When the "Show in share panel"
 * preference is enabled, the alias component is enabled at runtime so the app
 * becomes a target for ACTION_SEND (text / video).
 */
object SharePanelController {

    private fun aliasComponentName(context: Context): ComponentName =
        ComponentName(context, "${context.packageName}.ShareActivityAlias")

    /** Applies [enabled] to the share alias component without killing the process. */
    fun apply(context: Context, enabled: Boolean) {
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        context.packageManager.setComponentEnabledSetting(
            aliasComponentName(context),
            state,
            PackageManager.DONT_KILL_APP,
        )
    }

    /** Returns true when the share alias is currently enabled for ACTION_SEND. */
    fun isEnabled(context: Context): Boolean {
        return context.packageManager.getComponentEnabledSetting(aliasComponentName(context)) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }
}
