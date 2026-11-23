package com.neonnet.vpn

import android.app.*
import android.content.Intent
import android.net.VpnService
import android.net.ConnectivityManager
import android.os.Build
import android.os.IBinder
import android.system.Os
import androidx.core.app.NotificationCompat
import org.json.JSONObject

class NeonVpnService : VpnService() {
    private var tun: android.os.ParcelFileDescriptor? = null
    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopTunnel()
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, notification("در حال راه‌اندازی Xray"))
                val raw = intent.getStringExtra(EXTRA_CONFIG)
                if (raw.isNullOrBlank()) { stopTunnel(); return START_NOT_STICKY }
                Thread { startTunnel(raw) }.start()
            }
        }
        return START_NOT_STICKY
    }

    private fun startTunnel(raw: String) {
        try {
            val cfg = JSONObject(raw)
            val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val physicalInterface = cm.getLinkProperties(cm.activeNetwork)?.interfaceName
                ?: error("رابط شبکهٔ اصلی پیدا نشد؛ اتصال VPN متوقف شد تا حلقهٔ مسیریابی ایجاد نشود")
            val builder = Builder().setSession("NeonNet • Xray")
                .setMtu(1500)
                .addAddress("10.0.0.2", 30)
                .addAddress("fd00::2", 126)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)
            // Split tunneling uses Android's VpnService allow/disallow application APIs.
            val prefs = getSharedPreferences("neonnet", MODE_PRIVATE)
            val packages = prefs.getStringSet("excluded_packages", emptySet()) ?: emptySet()
            packages.forEach { pkg -> try { builder.addDisallowedApplication(pkg) } catch (_: Exception) {} }
            tun = builder.establish() ?: error("Android اجازه ساخت تونل را نداد")
            val fd = tun!!.fd
            // Xray's Android TUN integration reads this environment variable before RunXray.
            Os.setenv("XRAY_TUN_FD", fd.toString(), true)
            val xrayJson = XrayConfig.build(cfg.getString("uri"), fd, physicalInterface)
            LibXray.invoke("testXray", JSONObject().put("xrayJson", xrayJson))
            LibXray.invoke("runXray", JSONObject().put("xrayJson", xrayJson))
            running = true
            updateNotification("متصل • Xray-core")
            broadcastStatus(true, "connected")
        } catch (e: Exception) {
            running = false
            tun?.close(); tun = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            broadcastStatus(false, e.message ?: "خطای اتصال")
        }
    }

    private fun stopTunnel() {
        try { if (running) LibXray.invoke("stopXray") } catch (_: Exception) {}
        running = false
        try { tun?.close() } catch (_: Exception) {}
        tun = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        broadcastStatus(false, "disconnected")
    }
    private fun notification(text: String): Notification {
        val channel = "neonnet_vpn"
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(channel, "NeonNet VPN", NotificationManager.IMPORTANCE_LOW))
        }
        return NotificationCompat.Builder(this, channel).setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("NeonNet").setContentText(text).setOngoing(true).build()
    }
    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
    }
    private fun broadcastStatus(ok: Boolean, msg: String) {
        sendBroadcast(Intent(ACTION_STATUS).setPackage(packageName).putExtra("connected", ok).putExtra("message", msg))
    }
    companion object {
        const val ACTION_START = "com.neonnet.vpn.START"
        const val ACTION_STOP = "com.neonnet.vpn.STOP"
        const val ACTION_STATUS = "com.neonnet.vpn.STATUS"
        const val EXTRA_CONFIG = "config"
        const val NOTIFICATION_ID = 911
        @Volatile var running = false
    }
}
