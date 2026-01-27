package iwakura.lain.wheelvpn.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import iwakura.lain.wheelvpn.R
import iwakura.lain.wheelvpn.model.VpnConfig
import iwakura.lain.wheelvpn.util.ConfigConverter
import iwakura.lain.wheelvpn.util.Prefs
import iwakura.lain.wheelvpn.service.WheelVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URL
import java.util.Base64
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = Prefs(application)
    private val _configs = MutableStateFlow<List<VpnConfig>>(emptyList())
    val configs = _configs.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    private val _ping = MutableStateFlow<String?>(null)
    val ping = _ping.asStateFlow()

    private val _subscriptions = MutableStateFlow<List<MainViewModel.SubscriptionData>>(emptyList())
    val subscriptions = _subscriptions.asStateFlow()

    private val pingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val pingValue = intent?.getStringExtra("ping")
            Log.d("WheelVPN", "ViewModel received ping: $pingValue")
            _ping.value = pingValue
        }
    }

    private val _selectedConfigId = MutableStateFlow(prefs.selectedConfigId)
    val selectedConfigId = _selectedConfigId.asStateFlow()

    init {
        loadConfigs()
        loadSubscriptions()
        val filter = IntentFilter("iwakura.lain.wheelvpn.PING_UPDATE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(pingReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            application.registerReceiver(pingReceiver, filter)
        }
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().unregisterReceiver(pingReceiver)
    }

    private fun loadConfigs() {
        val ids = prefs.configIds
        val loadedConfigs = ids.mapNotNull { id ->
            val json = prefs.getConfigJson(id)
            val name = prefs.getConfigName(id)
            val subId = prefs.getConfigSubId(id)
            if (json != null && name != null) {
                VpnConfig(id, name, "", json, subId = subId)
            } else null
        }
        _configs.value = loadedConfigs
    }

    private fun loadSubscriptions() {
        _subscriptions.value = prefs.subscriptionIds.map { id ->
            MainViewModel.SubscriptionData(
                id = id,
                name = prefs.getSubscriptionName(id),
                announce = prefs.getSubscriptionAnnounce(id),
                announceUrl = prefs.getSubscriptionAnnounceUrl(id),
                userinfo = prefs.getSubscriptionUserinfo(id)
            )
        }
    }

    private fun getString(resId: Int, vararg args: Any): String {
        return getApplication<Application>().getString(resId, *args)
    }

    fun importFromClipboard(content: String) {
        if (content.isBlank()) {
            Toast.makeText(getApplication(), getString(R.string.toast_buffer_empty), Toast.LENGTH_SHORT).show()
            return
        }
        
        val urls = if (content.startsWith("vless://")) {
            listOf(content)
        } else {
            // tryin to decode base64 subscription
            try {
                val decoded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    String(Base64.getDecoder().decode(content.trim()))
                } else {
                    String(android.util.Base64.decode(content.trim(), android.util.Base64.DEFAULT))
                }
                decoded.split("\n").filter { it.isNotBlank() }
            } catch (e: Exception) {
                listOf(content)
            }
        }

        var importedCount = 0
        urls.forEach { url ->
            ConfigConverter.convertVless(url).onSuccess { config ->
                prefs.saveConfig(config.id, config.name, config.jsonConfig)
                importedCount++
            }
        }

        if (importedCount > 0) {
            loadConfigs()
            if (prefs.selectedConfigId == null && _configs.value.isNotEmpty()) {
                selectConfig(_configs.value.first().id)
            }
            Toast.makeText(getApplication(), getString(R.string.toast_imported, importedCount.toString()), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(getApplication(), getString(R.string.toast_import_error, "No valid configs found"), Toast.LENGTH_LONG).show()
        }
    }

    fun addSubscription(name: String, url: String) {
        val id = UUID.randomUUID().toString()
        prefs.saveSubscription(id, name, url)
        loadSubscriptions()
        Toast.makeText(getApplication(), getString(R.string.toast_sub_added), Toast.LENGTH_SHORT).show()
    }

    fun updateSubscriptions() {
        val context = getApplication<Application>()
        val subIds = prefs.subscriptionIds
        val appVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
        
        kotlinx.coroutines.MainScope().launch {
            var totalImported = 0
            subIds.forEach { id ->
                val url = prefs.getSubscriptionUrl(id)
                try {
                    val content = withContext(Dispatchers.IO) {
                        Log.d("WheelVPN", "Fetching subscription from: $url")
                        val connection = URL(url).openConnection() as java.net.HttpURLConnection
                        
                        connection.setRequestProperty("User-Agent", "v2rayNG/1.8.5")
                        connection.addRequestProperty("User-Agent", "WheelVPN/$appVersion")
                        
                        connection.connectTimeout = 15000
                        connection.readTimeout = 15000
                        
                        val responseCode = connection.responseCode
                        if (responseCode != 200) throw IOException("HTTP $responseCode")

                        val announce = connection.getHeaderField("announce")
                        val announceUrl = connection.getHeaderField("announce-url")
                        val userinfo = connection.getHeaderField("subscription-userinfo")
                        
                        if (announce != null || userinfo != null) {
                            prefs.saveSubscriptionInfo(id, announce, announceUrl, userinfo)
                        }

                        val bytes = connection.inputStream.use { it.readBytes() }
                        Log.d("WheelVPN", "Response code: $responseCode, length: ${bytes.size} bytes")
                        
                        String(bytes)
                    }
                    
                    if (content.isBlank()) return@forEach

                    _configs.value.filter { it.subId == id }.forEach { 
                        prefs.deleteConfig(it.id) 
                    }

                    val urls = try {
                        val decoded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            String(Base64.getDecoder().decode(content.trim()))
                        } else {
                            String(android.util.Base64.decode(content.trim(), android.util.Base64.DEFAULT))
                        }
                        decoded.split("\n").filter { it.isNotBlank() }
                    } catch (e: Exception) {
                        content.split("\n").filter { it.isNotBlank() }
                    }

                    urls.forEach { vUrl ->
                        ConfigConverter.convertVless(vUrl).onSuccess { config ->
                            prefs.saveConfig(config.id, config.name, config.jsonConfig, subId = id)
                            totalImported++
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, getString(R.string.toast_sub_error, e.message ?: ""), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            if (totalImported > 0) {
                loadSubscriptions() // Reload to get new headers/announce
                loadConfigs()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, getString(R.string.toast_subs_updated), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun selectConfig(id: String) {
        prefs.selectedConfigId = id
        _selectedConfigId.value = id
    }

    fun deleteConfig(id: String) {
        prefs.deleteConfig(id)
        if (prefs.selectedConfigId == id) {
            prefs.selectedConfigId = null
            _selectedConfigId.value = null
        }
        loadConfigs()
        Toast.makeText(getApplication(), getString(R.string.toast_config_deleted), Toast.LENGTH_SHORT).show()
    }

    fun getSubscriptions(): List<SubscriptionData> {
        return prefs.subscriptionIds.map { id ->
            SubscriptionData(
                id = id,
                name = prefs.getSubscriptionName(id),
                announce = prefs.getSubscriptionAnnounce(id),
                announceUrl = prefs.getSubscriptionAnnounceUrl(id),
                userinfo = prefs.getSubscriptionUserinfo(id)
            )
        }
    }

    data class SubscriptionData(
        val id: String,
        val name: String,
        val announce: String?,
        val announceUrl: String?,
        val userinfo: String?
    )

    fun deleteSubscription(id: String) {
        _configs.value.filter { it.subId == id }.forEach { 
            prefs.deleteConfig(it.id) 
        }
        prefs.subscriptionIds = prefs.subscriptionIds.filter { it != id }.toSet()
        loadSubscriptions()
        loadConfigs()
        Toast.makeText(getApplication(), "Subscription deleted", Toast.LENGTH_SHORT).show()
    }

    fun toggleConnection() {
        val context = getApplication<Application>()
        val intent = Intent(context, WheelVpnService::class.java)
        if (_isConnected.value) {
            intent.action = "STOP"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            _isConnected.value = false
            _ping.value = null
        } else {
            val currentId = prefs.selectedConfigId
            if (currentId != null) {
                intent.action = "START"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                _isConnected.value = true
                startPingLoop()
            } else {
                Toast.makeText(context, getString(R.string.toast_select_config_first), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startPingLoop() {
        // ping is now handled by WheelVpnService via catray.measureDelay
    }
}
