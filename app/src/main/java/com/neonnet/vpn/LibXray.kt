package com.neonnet.vpn

import org.json.JSONObject

/**
 * Adapter for the official XTLS/libXray gomobile AAR.
 * The AAR exposes package libXray with static Invoke(String): String.
 */
object LibXray {
    fun invoke(method: String, payload: JSONObject = JSONObject()): JSONObject {
        val request = JSONObject()
            .put("apiVersion", 3)
            .put("method", method)
            .put("payload", payload)
            .toString()
        val clazz = try { Class.forName("libXray.LibXray") }
                    catch (e: Exception) { throw IllegalStateException("فایل رسمی libXray.aar در app/libs قرار نگرفته است", e) }
        val invoke = clazz.methods.firstOrNull { it.name.equals("invoke", true) && it.parameterTypes.contentEquals(arrayOf(String::class.java)) }
            ?: throw IllegalStateException("تابع Invoke در AAR پیدا نشد؛ نسخه libXray را بررسی کن")
        val result = invoke.invoke(null, request) as? String ?: error("پاسخ نامعتبر از Xray")
        val json = JSONObject(result)
        if (!json.optBoolean("success")) throw IllegalStateException(json.optString("error", "خطای Xray"))
        return json
    }
}
