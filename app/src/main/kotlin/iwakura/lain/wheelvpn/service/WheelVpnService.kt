package iwakura.lain.wheelvpn.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import catray.CoreCallbackHandler
import catray.CoreController
import catray.Catray
import iwakura.lain.wheelvpn.MainActivity
import iwakura.lain.wheelvpn.R
import iwakura.lain.wheelvpn.util.Prefs
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException
import go.Seq

class WheelVpnService : VpnService() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var coreController: CoreController? = null
    private var tunFd: ParcelFileDescriptor? = null
    
    private val TAG = "WheelVpnService"

    override fun onCreate() {
        super.onCreate()
        Seq.setContext(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showNotification() // call immediately
        val action = intent?.action
        Log.d(TAG, "Action received: $action")
        
        when (action) {
            "START" -> startVpn()
            "STOP" -> stopVpn()
            else -> startVpn()
        }
        
        return START_STICKY
    }

    private fun startVpn() {
        val prefs = Prefs(this)
        val configId = prefs.selectedConfigId ?: return
        val configJson = prefs.getConfigJson(configId) ?: return
        
        serviceScope.launch {
            try {
                // Initialize Catray environment
                Catray.initCoreEnv(filesDir.absolutePath, "")
                
                coreController = Catray.newCoreController(object : CoreCallbackHandler {
                    override fun startup(): Long = 0
                    override fun shutdown(): Long {
                        stopVpn()
                        return 0
                    }
                    override fun onEmitStatus(status: Long, message: String?): Long {
                        Log.d(TAG, "Catray status [$status]: $message")
                        return 0
                    }
                })

                tunFd = Builder()
                    .setSession(getString(R.string.vpn_session_name))
                    .setMtu(1500)
                    .addAddress("10.0.0.1", 24)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer("1.1.1.1")
                    .addDisallowedApplication(packageName) // exclude self to avoid loop
                    .establish()
                
                tunFd?.let { fd ->
                    coreController?.startLoop(configJson, fd.fd)

                    val tproxyConf = createTproxyConf(prefs.socksPort)
                    val confFile = File(cacheDir, "tproxy.conf")
                    confFile.writeText(tproxyConf)
                    
                    TProxyService.TProxyStartService(confFile.absolutePath, fd.fd)
                   startPingMeasurement()
                }
                
                Log.d(TAG, "VPN Started successfully via Catray")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error starting VPN", e)
                stopVpn()
            }
        }
    }

    private fun startPingMeasurement() {
        serviceScope.launch {
            while (coreController != null) {
                try {
                    // Use measureDelay from catray which uses the core's transport
                    val delay = coreController?.measureDelay("https://204.lain1.dev/generate_204") ?: -1L
                    Log.d(TAG, "Ping measurement: ${delay}ms")
                    val pingStr = if (delay >= 0) "${delay}ms" else "Error"
                    
                    val intent = Intent("iwakura.lain.wheelvpn.PING_UPDATE")
                    intent.setPackage(packageName)
                    intent.putExtra("ping", pingStr)
                    sendBroadcast(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Ping error", e)
                }
                delay(5000)
            }
        }
    }

    private fun createTproxyConf(port: Int): String {
        return """
            tunnel:
              mtu: 1500
            socks5:
              port: $port
              address: 127.0.0.1
              udp: udp
        """.trimIndent()
    }

    private fun stopVpn() {
        try {
            TProxyService.TProxyStopService()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping tproxy", e)
        }
        try {
            coreController?.stopLoop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Catray", e)
        }
        coreController = null
        tunFd?.close()
        tunFd = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun showNotification() {
        val channelId = "vpn_channel"
        val channelName = getString(R.string.notification_channel_name)
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }
        
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.vpn_running))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .build()
            
        startForeground(1, notification)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        stopVpn()
        super.onDestroy()
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    companion object {
        init {
            System.loadLibrary("hev-socks5-tunnel")
        }
    }
}
