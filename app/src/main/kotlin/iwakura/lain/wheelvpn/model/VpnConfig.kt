package iwakura.lain.wheelvpn.model

data class VpnConfig(
    val id: String,
    val name: String,
    val rawUrl: String,
    val jsonConfig: String,
    val type: String = "VLESS",
    val subId: String? = null
)
