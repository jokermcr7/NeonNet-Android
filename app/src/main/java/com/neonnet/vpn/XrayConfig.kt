package com.neonnet.vpn

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

object XrayConfig {
    fun build(uriText: String, tunFd: Int, physicalInterface: String): String {
        val uri = Uri.parse(uriText)
        require(uri.scheme == "vless") { "فقط VLESS پشتیبانی می‌شود" }
        val host = uri.host ?: error("آدرس سرور نامعتبر است")
        val port = uri.port.takeIf { it > 0 } ?: error("پورت نامعتبر است")
        val uuid = uri.userInfo?.substringBefore(':') ?: error("UUID وجود ندارد")
        val q = uri.queryParameterNames
        fun p(k: String, d: String = "") = uri.getQueryParameter(k) ?: d
        val user = JSONObject().put("id", uuid).put("encryption", "none")
        if (p("flow").isNotBlank()) user.put("flow", p("flow"))
        val vnext = JSONObject().put("address", host).put("port", port)
            .put("users", JSONArray().put(user))
        val outbound = JSONObject().put("tag", "proxy").put("protocol", "vless")
            .put("settings", JSONObject().put("vnext", JSONArray().put(vnext)))
        val network = p("type", "tcp")
        val stream = JSONObject().put("network", network)
        when (p("security", "none")) {
            "reality" -> stream.put("security", "reality").put("realitySettings",
                JSONObject().put("show", false).put("serverName", p("sni"))
                    .put("fingerprint", p("fp", "chrome")).put("publicKey", p("pbk", p("publicKey")))
                    .put("shortId", p("sid")).put("spiderX", p("spx", "/")))
            "tls" -> stream.put("security", "tls").put("tlsSettings",
                JSONObject().put("serverName", p("sni")).put("fingerprint", p("fp", "chrome")))
            else -> stream.put("security", "none")
        }
        when (network) {
            "ws" -> stream.put("wsSettings", JSONObject().put("path", p("path", "/")))
            "grpc" -> stream.put("grpcSettings", JSONObject().put("serviceName", p("serviceName")))
            "httpupgrade" -> stream.put("httpupgradeSettings", JSONObject().put("path", p("path", "/")))
        }
        outbound.put("streamSettings", stream)
        val tunSettings = JSONObject()
            .put("name", "neonnet0").put("mtu", 1500)
            .put("gateway", JSONArray().put("10.0.0.1/30").put("fd00::1/126"))
            .put("dns", JSONArray().put("1.1.1.1").put("8.8.8.8"))
            .put("autoOutboundsInterface", physicalInterface)
        val inbound = JSONObject().put("tag", "tun-in").put("protocol", "tun").put("settings", tunSettings)
        val routing = JSONObject().put("domainStrategy", "AsIs")
            .put("rules", JSONArray().put(JSONObject().put("type", "field").put("inboundTag", JSONArray().put("tun-in")).put("outboundTag", "proxy")))
        return JSONObject().put("log", JSONObject().put("loglevel", "warning"))
            .put("inbounds", JSONArray().put(inbound))
            .put("outbounds", JSONArray().put(outbound).put(JSONObject().put("tag", "direct").put("protocol", "freedom")))
            .put("routing", routing).toString()
    }
}
