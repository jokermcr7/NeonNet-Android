package com.neonnet.vpn

import org.json.JSONArray
import org.json.JSONObject

/** Builds an Android TUN configuration around libXray's official share-link parser. */
object XrayConfig {
    /**
     * Converts a supported Xray share link (VLESS/VMess AEAD/Trojan/Shadowsocks/SOCKS)
     * into a complete Android TUN configuration. Parsing is delegated to the exact
     * libXray artifact bundled by the build workflow; we do not hand-roll protocol JSON.
     */
    fun build(shareLink: String, tunFd: Int, physicalInterface: String): String {
        require(shareLink.isNotBlank()) { "لینک کانفیگ خالی است" }
        require(tunFd >= 0) { "شناسه TUN نامعتبر است" }
        require(physicalInterface.isNotBlank()) { "رابط اینترنت اصلی پیدا نشد" }

        val converted = LibXray.invoke(
            "convertShareLinksToXrayJson",
            JSONObject().put("text", shareLink.trim())
        )
        val data = converted.optJSONObject("data")
            ?: error("پاسخ مبدل کانفیگ از libXray خالی است")
        val sourceOutbounds = data.optJSONArray("outbounds")
            ?: error("هیچ خروجی معتبری در کانفیگ پیدا نشد")
        require(sourceOutbounds.length() > 0) { "هیچ سرور قابل استفاده‌ای در کانفیگ پیدا نشد" }

        // libXray share-link conversion validates the outbound using the bundled Xray core.
        // Use a stable tag so the TUN routing rule always points at the selected server.
        val outbounds = JSONArray()
        var proxyTag: String? = null
        for (index in 0 until sourceOutbounds.length()) {
            val outbound = sourceOutbounds.optJSONObject(index)?.let { JSONObject(it.toString()) }
                ?: continue
            val tag = if (index == 0) "proxy" else outbound.optString("tag").ifBlank { "proxy-$index" }
            outbound.put("tag", tag)
            if (proxyTag == null) proxyTag = tag
            outbounds.put(outbound)
        }
        require(outbounds.length() > 0 && proxyTag != null) { "خروجی معتبر Xray ساخته نشد" }
        outbounds.put(JSONObject().put("tag", "direct").put("protocol", "freedom"))

        val tunSettings = JSONObject()
            .put("name", "neonnet0")
            .put("mtu", 1500)
            .put("gateway", JSONArray().put("10.0.0.1/30").put("fd00::1/126"))
            .put("dns", JSONArray().put("1.1.1.1").put("8.8.8.8"))
            // Android requires an explicit physical interface name for socket binding.
            .put("autoOutboundsInterface", physicalInterface)

        val inbound = JSONObject()
            .put("tag", "tun-in")
            .put("protocol", "tun")
            .put("settings", tunSettings)

        val routingRule = JSONObject()
            .put("type", "field")
            .put("inboundTag", JSONArray().put("tun-in"))
            .put("outboundTag", proxyTag)

        // libXray v3 reads the Android VpnService FD from the Xray config's root env.
        // A process-level setenv alone is not the supported integration contract.
        return JSONObject()
            .put("env", JSONObject().put("xray.tun.fd", tunFd.toString()))
            .put("log", JSONObject().put("loglevel", "warning"))
            .put("inbounds", JSONArray().put(inbound))
            .put("outbounds", outbounds)
            .put("routing", JSONObject()
                .put("domainStrategy", "AsIs")
                .put("rules", JSONArray().put(routingRule)))
            .toString()
    }
}
