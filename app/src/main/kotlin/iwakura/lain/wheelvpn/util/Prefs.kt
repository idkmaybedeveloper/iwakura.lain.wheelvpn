package iwakura.lain.wheelvpn.util

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("wheel_vpn_prefs", Context.MODE_PRIVATE)

    var selectedConfigId: String?
        get() = prefs.getString("selected_config_id", null)
        set(value) = prefs.edit().putString("selected_config_id", value).apply()

    var socksPort: Int
        get() = prefs.getInt("socks_port", 10808)
        set(value) = prefs.edit().putInt("socks_port", value).apply()

    var configIds: Set<String>
        get() = prefs.getStringSet("config_ids", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("config_ids", value).apply()

    fun getConfigJson(id: String): String? {
        return prefs.getString("config_json_$id", null)
    }

    fun getConfigName(id: String): String? {
        return prefs.getString("config_name_$id", "Unnamed")
    }

    fun getConfigSubId(id: String): String? {
        return prefs.getString("config_sub_id_$id", null)
    }

    fun saveConfig(id: String, name: String, json: String, subId: String? = null) {
        prefs.edit()
            .putString("config_json_$id", json)
            .putString("config_name_$id", name)
            .putString("config_sub_id_$id", subId)
            .putStringSet("config_ids", configIds + id)
            .apply()
    }

    fun deleteConfig(id: String) {
        prefs.edit()
            .remove("config_json_$id")
            .remove("config_name_$id")
            .remove("config_sub_id_$id")
            .putStringSet("config_ids", configIds.filter { it != id }.toSet())
            .apply()
    }

    var subscriptionIds: Set<String>
        get() = prefs.getStringSet("sub_ids", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("sub_ids", value).apply()

    fun saveSubscription(id: String, name: String, url: String) {
        prefs.edit()
            .putString("sub_name_$id", name)
            .putString("sub_url_$id", url)
            .putStringSet("sub_ids", subscriptionIds + id)
            .apply()
    }

    fun saveSubscriptionInfo(id: String, announce: String?, announceUrl: String?, userinfo: String?) {
        prefs.edit().apply {
            putString("sub_announce_$id", announce)
            putString("sub_announce_url_$id", announceUrl)
            putString("sub_userinfo_$id", userinfo)
            apply()
        }
    }

    fun getSubscriptionAnnounce(id: String) = prefs.getString("sub_announce_$id", null)
    fun getSubscriptionAnnounceUrl(id: String) = prefs.getString("sub_announce_url_$id", null)
    fun getSubscriptionUserinfo(id: String) = prefs.getString("sub_userinfo_$id", null)

    fun getSubscriptionName(id: String) = prefs.getString("sub_name_$id", "") ?: ""
    fun getSubscriptionUrl(id: String) = prefs.getString("sub_url_$id", "") ?: ""
}
