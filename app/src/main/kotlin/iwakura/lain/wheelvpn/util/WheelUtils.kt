package iwakura.lain.wheelvpn.util

import android.content.ClipboardManager
import android.content.Context
import android.util.Base64
import android.util.Log
import java.net.URLDecoder
import java.net.URLEncoder

object WheelUtils {
    private const val TAG = "WheelVPN.Utils"

    fun decode(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        return try {
            Base64.decode(text, Base64.NO_WRAP).toString(Charsets.UTF_8)
        } catch (e: Exception) {
            try {
                Base64.decode(text, Base64.NO_WRAP or Base64.URL_SAFE).toString(Charsets.UTF_8)
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to decode base64", e2)
                ""
            }
        }
    }

    fun encode(text: String): String {
        return try {
            Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encode base64", e)
            ""
        }
    }

    fun urlDecode(url: String): String {
        return try {
            URLDecoder.decode(url, Charsets.UTF_8.toString())
        } catch (e: Exception) {
            url
        }
    }

    fun urlEncode(url: String): String {
        return try {
            URLEncoder.encode(url, Charsets.UTF_8.toString())
        } catch (e: Exception) {
            url
        }
    }

    fun getClipboard(context: Context): String {
        return try {
            val cmb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cmb.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}
