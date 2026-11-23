package com.neonnet.vpn

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.webkit.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private var pendingConfig: String? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private val filePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        fileCallback?.onReceiveValue(if (uri == null) null else arrayOf(uri))
        fileCallback = null
    }
    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) pendingConfig?.let { startVpn(it) }
        else toast("مجوز VPN داده نشد")
    }
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) toast("مجوز دوربین لازم است")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        setContentView(web)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val u = request?.url ?: return false
                return if (u.scheme == "file" || u.host == null) false else {
                    // External links are intentionally not navigated inside the trusted local UI.
                    true
                }
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(view: WebView?, callback: ValueCallback<Array<Uri>>?, params: FileChooserParams?): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                filePicker.launch("*/*")
                return true
            }
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    val allowed = request.resources.all { it == PermissionRequest.RESOURCE_VIDEO_CAPTURE }
                    if (allowed && ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) request.grant(request.resources)
                    else request.deny()
                }
            }
        }
        web.addJavascriptInterface(Bridge(), "Android")
        web.loadUrl("file:///android_asset/www/dashboard.html")
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 71)
        }
    }

    inner class Bridge {
        @JavascriptInterface fun connect(configJson: String) {
            runOnUiThread {
                pendingConfig = configJson
                val prepare = VpnService.prepare(this@MainActivity)
                if (prepare != null) vpnPermission.launch(prepare) else startVpn(configJson)
            }
        }
        @JavascriptInterface fun disconnect() {
            startService(Intent(this@MainActivity, NeonVpnService::class.java).setAction(NeonVpnService.ACTION_STOP))
        }
        @JavascriptInterface fun status(): String = JSONObject().put("connected", NeonVpnService.running).toString()
        @JavascriptInterface fun copy(text: String) {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("NeonNet", text))
            runOnUiThread { toast("کپی شد") }
        }
        @JavascriptInterface fun requestCamera() {
            runOnUiThread {
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
                    cameraPermission.launch(Manifest.permission.CAMERA)
            }
        }
        @JavascriptInterface fun hasCameraPermission(): Boolean =
            ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        @JavascriptInterface fun readClipboard(): String {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            return if (clipboard.hasPrimaryClip() && clipboard.primaryClip!!.itemCount > 0)
                clipboard.primaryClip!!.getItemAt(0).coerceToText(this@MainActivity).toString() else ""
        }
        @JavascriptInterface fun installedApps(): String = AppCatalog(this@MainActivity).asJson()
        @JavascriptInterface fun setExcludedApps(packagesJson: String) {
            try {
                val arr = org.json.JSONArray(packagesJson)
                val set = mutableSetOf<String>()
                for (i in 0 until arr.length()) set.add(arr.getString(i))
                getSharedPreferences("neonnet", MODE_PRIVATE).edit().putStringSet("excluded_packages", set).apply()
            } catch (_: Exception) { }
        }
    }

    private fun startVpn(config: String) {
        val i = Intent(this, NeonVpnService::class.java).setAction(NeonVpnService.ACTION_START)
        i.putExtra(NeonVpnService.EXTRA_CONFIG, config)
        ContextCompat.startForegroundService(this, i)
    }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    override fun onBackPressed() {
        if (::web.isInitialized && web.canGoBack()) web.goBack() else super.onBackPressed()
    }
}
