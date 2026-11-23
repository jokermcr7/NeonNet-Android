package com.neonnet.vpn

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

class AppCatalog(private val context: Context) {
    @Suppress("DEPRECATION")
    fun asJson(): String {
        val pm = context.packageManager
        val apps = if (Build.VERSION.SDK_INT >= 33) pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
                   else pm.getInstalledApplications(0)
        val out = JSONArray()
        apps.filter { it.packageName != context.packageName && pm.getLaunchIntentForPackage(it.packageName) != null }
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
            .forEach { out.put(JSONObject().put("package", it.packageName).put("label", pm.getApplicationLabel(it).toString())) }
        return out.toString()
    }
}
