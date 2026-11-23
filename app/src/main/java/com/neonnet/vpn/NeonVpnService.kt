package com.neonnet.vpn

import android.app.*
import android.content.Intent
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.util.concurrent.Executors

class NeonVpnService : VpnService() {
    private var tun: android.os.ParcelFileDescriptor? = null
    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread(r, "NeonNet-Xray").apply { isDaemon = true }
    }
    @Volatile private var xrayStarted = false
    @Volatile private var dnsConfigured = false

    private val dialerController = object : libXray.DialerController {
        override fun protectFd(fd: Long): Boolean = protect(fd.toInt())
    }

    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> worker.execute { stopTunnel("disconnected") }
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, notification("در حال راه‌اندازی Xray"))
                val raw = intent.getStringExtra(EXTRA_CONFIG)
                if (raw.isNullOrBlank()) {
                    worker.execute { stopTunnel("کانفیگ خالی است") }
                    return START_NOT_STICKY
                }
                worker.execute { startTunnel(raw) }
            }
        }
        return START_NOT_STICKY
    }

    private fun startTunnel(raw: String) {
        try {
            // Serialize starts and stop any previous managed core before replacing its TUN.
            if (xrayStarted || dnsConfigured) stopCore()
            closeTun()
            running = false
            lastMessage = "در حال آماده‌سازی تونل"
            broadcastStatus(false, lastMessage)

            val cfg = JSONObject(raw)
            val uri = cfg.optString("uri").trim()
            require(uri.isNotBlank()) { "لینک سرور در کانفیگ وجود ندارد" }

            val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: error("اتصال اینترنت فعالی پیدا نشد")
            val physicalInterface = cm.getLinkProperties(network)?.interfaceName
                ?.takeIf { it.isNotBlank() }
                ?: error("نام رابط شبکه اصلی پیدا نشد؛ برای جلوگیری از حلقه مسیریابی اتصال متوقف شد")

            val builder = Builder()
                .setSession("NeonNet • Xray")
                .setMtu(1500)
                .addAddress("10.0.0.2", 30)
                .addAddress("fd00::2", 126)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)

            val prefs = getSharedPreferences("neonnet", MODE_PRIVATE)
            val excluded = prefs.getStringSet("excluded_packages", emptySet()) ?: emptySet()
            excluded.forEach { pkg ->
                try { builder.addDisallowedApplication(pkg) }
                catch (e: Exception) { /* Ignore packages removed since the selection was saved. */ }
            }

            tun = builder.establish() ?: error("Android اجازه ساخت تونل VPN را نداد")

            libXray.LibXray.registerDialerController(dialerController)
            dnsConfigured = true
            libXray.LibXray.setDNS(dialerController, "1.1.1.1:53")

            val xrayJson = XrayConfig.build(uri, tun!!.fd, physicalInterface)

            // testXray validates core construction only; runXray below is the actual startup.
            LibXray.invoke("testXray", JSONObject().put("xrayJson", xrayJson))
            LibXray.invoke("runXray", JSONObject().put("xrayJson", xrayJson))
            xrayStarted = true

            // Confirm the managed instance reports running before showing Connected.
            val state = LibXray.invoke("getXrayState")
                .optJSONObject("data")
                ?: error("وضعیت موتور Xray قابل خواندن نیست")
            if (!state.optBoolean("running", false)) error("موتور Xray پس از اجرا فعال نشد")

            running = true
            lastMessage = "connected"
            updateNotification("متصل • Xray-core")
            broadcastStatus(true, lastMessage)
        } catch (e: Exception) {
            val message = e.message?.takeIf { it.isNotBlank() } ?: "خطای ناشناخته در اتصال"
            try { stopCore() } catch (_: Exception) {}
            closeTun()
            running = false
            lastMessage = message
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            broadcastStatus(false, message)
        }
    }

    private fun stopCore() {
        try {
            if (xrayStarted) LibXray.invoke("stopXray")
        } finally {
            xrayStarted = false
            if (dnsConfigured) {
                try { libXray.LibXray.resetDNS() }
                finally { dnsConfigured = false }
            }
        }
    }

    private fun closeTun() {
        try { tun?.close() } catch (_: Exception) {}
        tun = null
    }

    private fun stopTunnel(message: String) {
        try { stopCore() } catch (_: Exception) {}
        closeTun()
        running = false
        lastMessage = message
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        broadcastStatus(false, message)
    }

    override fun onDestroy() {
        // The worker is single-threaded; do not block the main thread waiting for native shutdown.
        worker.execute {
            try { stopCore() } catch (_: Exception) {}
            closeTun()
            running = false
        }
        worker.shutdown()
        super.onDestroy()
    }

    private fun notification(text: String): Notification {
        val channel = "neonnet_vpn"
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(channel, "NeonNet VPN", NotificationManager.IMPORTANCE_LOW))
        }
        return NotificationCompat.Builder(this, channel)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("NeonNet")
            .setContentText(text)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
    }

    private fun broadcastStatus(ok: Boolean, msg: String) {
        sendBroadcast(Intent(ACTION_STATUS).setPackage(packageName)
            .putExtra("connected", ok).putExtra("message", msg))
    }

    companion object {
        const val ACTION_START = "com.neonnet.vpn.START"
        const val ACTION_STOP = "com.neonnet.vpn.STOP"
        const val ACTION_STATUS = "com.neonnet.vpn.STATUS"
        const val EXTRA_CONFIG = "config"
        const val NOTIFICATION_ID = 911
        @Volatile var running = false
        @Volatile var lastMessage = "disconnected"
    }
}
