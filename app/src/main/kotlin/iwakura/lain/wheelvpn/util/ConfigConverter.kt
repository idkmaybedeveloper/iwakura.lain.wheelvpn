package iwakura.lain.wheelvpn.util

import android.net.Uri
import iwakura.lain.wheelvpn.model.VpnConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.MalformedURLException
import java.util.UUID

object ConfigConverter {

    fun convertVless(urlStr: String): Result<VpnConfig> {
        return try {
            val uri = Uri.parse(urlStr)
            if (uri.scheme != "vless") {
                return Result.failure(IllegalArgumentException("Not a VLESS link"))
            }

            val name = uri.fragment ?: "VLESS_${System.currentTimeMillis()}"
            val address = uri.host ?: return Result.failure(MalformedURLException("Missing host"))
            val port = if (uri.port != -1) uri.port else 443
            val userId = uri.userInfo ?: return Result.failure(MalformedURLException("Missing user info"))

            val type = uri.getQueryParameter("type") ?: "tcp"
            val security = uri.getQueryParameter("security") ?: "reality"
            val sni = uri.getQueryParameter("sni") ?: uri.getQueryParameter("peer") ?: address
            val fingerprint = uri.getQueryParameter("fp") ?: "chrome"
            val flow = uri.getQueryParameter("flow") ?: "xtls-rprx-vision"
            val realityPbk = uri.getQueryParameter("pbk") ?: ""
            val realityShortId = uri.getQueryParameter("sid") ?: ""

            val configJson = JSONObject().apply {
                put("log", JSONObject().apply { put("loglevel", "warning") })
                
                val inboundsArray = JSONArray()
                inboundsArray.put(JSONObject().apply {
                    put("port", 10808)
                    put("listen", "127.0.0.1")
                    put("protocol", "socks")
                    put("settings", JSONObject().apply { put("udp", true) })
                })
                put("inbounds", inboundsArray)

                val outboundsArray = JSONArray()
                outboundsArray.put(JSONObject().apply {
                    put("protocol", "vless")
                    put("settings", JSONObject().apply {
                        val vnextArray = JSONArray()
                        vnextArray.put(JSONObject().apply {
                            put("address", address)
                            put("port", port)
                            val usersArray = JSONArray()
                            usersArray.put(JSONObject().apply {
                                put("id", userId)
                                put("encryption", "none")
                                put("flow", flow)
                            })
                            put("users", usersArray)
                        })
                        put("vnext", vnextArray)
                    })
                    put("streamSettings", JSONObject().apply {
                        put("network", type)
                        put("security", security)
                        put("realitySettings", JSONObject().apply {
                            put("show", false)
                            put("fingerprint", fingerprint)
                            put("serverName", sni)
                            put("publicKey", realityPbk)
                            put("shortId", realityShortId)
                        })
                    })
                })
                put("outbounds", outboundsArray)
            }

            Result.success(
                VpnConfig(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    rawUrl = urlStr,
                    jsonConfig = configJson.toString(2)
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
