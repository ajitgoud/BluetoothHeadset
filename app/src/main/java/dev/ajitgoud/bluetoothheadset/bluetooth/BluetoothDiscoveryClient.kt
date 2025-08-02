package dev.ajitgoud.bluetoothheadset.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@SuppressLint("MissingPermission")
@Singleton
class BluetoothDiscoveryClient @Inject constructor(
    private val bluetoothAdapter: BluetoothAdapter
) {

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var receiver: BroadcastReceiver? = null

    private val _discoveryState: MutableSharedFlow<BluetoothDiscoveryState> = MutableSharedFlow(
        replay = 0,
        extraBufferCapacity = 30,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val discoveryState = _discoveryState.asSharedFlow()


    fun startDiscovery(
        context: Context
    ) {
        if (bluetoothAdapter.isDiscovering) bluetoothAdapter.cancelDiscovery()

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        coroutineScope.launch {
            try {
                receiver = getReceiver
                ContextCompat.registerReceiver(
                    context,
                    receiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
            } catch (e: Exception) {
                Timber.e("Failed to register discovery receiver: ${e.localizedMessage}")
                return@launch
            }

            bluetoothAdapter.startDiscovery()
            Timber.d("Discovery Started...")
        }
    }

    fun stopDiscovery(
        context: Context
    ) {

        if (bluetoothAdapter.isDiscovering) {
            receiver?.let {
                try {
                    receiver?.let { context.unregisterReceiver(it) }
                } catch (e: Exception) {
                    Timber.d("Failed to unregister discovery receiver: ${e.localizedMessage}")
                }
                receiver = null
            }

            bluetoothAdapter.cancelDiscovery()
            Timber.d("Discovery Stopped")
        }
    }


    private val getReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(
                            BluetoothDevice.EXTRA_DEVICE,
                            BluetoothDevice::class.java
                        )
                    } else {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) as BluetoothDevice?
                    }
                    device?.let { btDevice ->

                        btDevice.name?.let { name ->
                            updateDiscoveryState(
                                BluetoothDiscoveryState.BluetoothDeviceFound(
                                    btDevice
                                )
                            )
                        }
                    }
                }

                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    updateDiscoveryState(BluetoothDiscoveryState.DiscoveryStarted)
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    updateDiscoveryState(BluetoothDiscoveryState.DiscoveryStopped)
                }
            }
        }

    }

    private fun updateDiscoveryState(state: BluetoothDiscoveryState) {
        coroutineScope.launch {
            _discoveryState.emit(state)
        }
    }

    sealed interface BluetoothDiscoveryState {
        data object DiscoveryStarted : BluetoothDiscoveryState
        data object DiscoveryStopped : BluetoothDiscoveryState
        data class BluetoothDeviceFound(val device: BluetoothDevice) : BluetoothDiscoveryState
        data class Error(val message: String) : BluetoothDiscoveryState
    }
}