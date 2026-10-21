package com.rayscope.app.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

enum class ConnState { STOPPED, CONNECTING, CONNECTED, STOPPING }

data class VpnStatus(
    val state: ConnState = ConnState.STOPPED,
    val serverId: String? = null,
    val serverName: String = "",
    val since: Long = 0L,
    val upSpeed: Long = 0,
    val downSpeed: Long = 0,
    val totalUp: Long = 0,
    val totalDown: Long = 0,
    val peakUp: Long = 0,
    val peakDown: Long = 0,
    val livePing: Long = -2,
    val pingHistory: List<Long> = emptyList(),
    val error: String = "",
    /** last 60 seconds: (up, down) bytes per second */
    val history: List<Pair<Long, Long>> = emptyList(),
    val switches: Int = 0,
)

object VpnState {
    val status = MutableStateFlow(VpnStatus())

    fun update(f: (VpnStatus) -> VpnStatus) {
        status.update(f)
    }

    val isConnected: Boolean get() = status.value.state == ConnState.CONNECTED
}
