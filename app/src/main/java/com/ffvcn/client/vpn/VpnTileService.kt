package com.ffvcn.client.vpn

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.ffvcn.client.MainActivity
import com.ffvcn.client.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * The quick settings tile: one tap to connect or disconnect from the shade.
 *
 * The tile cannot ask for VPN consent by itself — that dialog belongs to an
 * activity — so when consent or an account is still missing it brings the app
 * forward and lets the user finish there. While the shade is open the tile
 * follows [VpnState], so it turns while connecting and lights up once the
 * tunnel is up, even if the connect was started somewhere else.
 */
class VpnTileService : TileService() {
    private var scope: CoroutineScope? = null

    override fun onTileAdded() {
        super.onTileAdded()
        refresh()
    }

    override fun onStartListening() {
        super.onStartListening()
        refresh()
        val listening = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = listening
        listening.launch {
            VpnState.ui.collect { refresh() }
        }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        when (VpnState.ui.value.phase) {
            Phase.On, Phase.Connecting -> FirefoxVpnService.stop(this)
            Phase.Off, Phase.Error -> connect()
        }
        refresh()
    }

    /** Starts where it can; hands back to the app where it must. */
    private fun connect() {
        if (!Store.hasSession(this)) {
            openApp()
            return
        }
        if (Store.mode(this) == Mode.Proxy) {
            FirefoxVpnService.start(this)
            return
        }
        if (VpnService.prepare(this) != null) {
            openApp()
            return
        }
        FirefoxVpnService.start(this)
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val phase = VpnState.ui.value.phase
        val running = phase == Phase.On || phase == Phase.Connecting

        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_ffv)
        tile.label = getString(R.string.tile_label)
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(
                when {
                    phase == Phase.On && Store.mode(this) == Mode.Proxy -> R.string.status_proxy_on
                    phase == Phase.On -> R.string.status_on
                    phase == Phase.Connecting -> R.string.status_connecting
                    phase == Phase.Error -> R.string.status_error
                    !Store.hasSession(this) -> R.string.tile_no_account
                    else -> R.string.status_off
                }
            )
        }
        tile.updateTile()
    }

    /** Collapses the shade and opens the app, the only way to get consent. */
    @Suppress("DEPRECATION")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        } else {
            startActivityAndCollapse(intent)
        }
    }
}
