package dev.ajitgoud.bluetoothheadset.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.lang.reflect.Method
import javax.inject.Inject
import javax.inject.Singleton


@SuppressLint("MissingPermission")
@Singleton
class BluetoothConnectClient @Inject constructor(private val bluetoothAdapter: BluetoothAdapter) {
    private val connectionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _connectionStateFlow: MutableStateFlow<BluetoothConnectionState> =
        MutableStateFlow(BluetoothConnectionState.Idle)

    val connectionStateFlow: StateFlow<BluetoothConnectionState> =
        _connectionStateFlow.asStateFlow()

    private var bondReceiver: BroadcastReceiver? = null


    fun updateConnectionState(state: BluetoothConnectionState) {
        connectionScope.launch {
            _connectionStateFlow.emit(state)
        }
    }

    fun connect(context: Context, device: BluetoothDevice) {
        if (!bluetoothAdapter.isEnabled) {
            updateConnectionState(
                BluetoothConnectionState.Error(
                    device,
                    "Please enable bluetooth..."
                )
            )
            return
        }

        when (device.bondState) {
            BluetoothDevice.BOND_NONE -> startPairing(context, device)
            BluetoothDevice.BOND_BONDED -> startConnection(context, device)
        }
    }


    private fun startPairing(
        context: Context,
        device: BluetoothDevice
    ) {
        Timber.d("State: Pairing device...")
        updateConnectionState(BluetoothConnectionState.Connecting("Pairing device..."))
        bondReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: Exception) {
            }
            bondReceiver = null
        }
        bondReceiver = createBondReceiver(context, device)
        ContextCompat.registerReceiver(
            context,
            bondReceiver,
            IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        device.createBond()
    }


    private fun startConnection(
        context: Context,
        device: BluetoothDevice
    ) {

        Timber.d("State: Connecting to device...")
        updateConnectionState(BluetoothConnectionState.Connecting("Connecting to device..."))
        bluetoothAdapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                if (profile == BluetoothProfile.HEADSET) {
                    val bluetoothHeadset = proxy as BluetoothHeadset
                    Timber.d("State: Got headset profile...")
                    connectUsingReflection(context, bluetoothHeadset, device)
                    bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, proxy)
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                Timber.d("Disconnected from the profile...")
            }
        }, BluetoothProfile.HEADSET)
    }

    private fun connectUsingReflection(
        context: Context, bluetoothHeadset: BluetoothHeadset, device: BluetoothDevice
    ) {
        connectionScope.launch {
            try {

                Timber.d("State: Connect device reflection...")
                val connectMethod: Method = bluetoothHeadset.javaClass.getDeclaredMethod(
                    "connect", BluetoothDevice::class.java
                )

                connectMethod.isAccessible = true
                connectMethod.invoke(bluetoothHeadset, device)
                val isConnected = waitForConnection(context, device)


                if (isConnected) {
                    updateConnectionState(BluetoothConnectionState.Connected(device))
                } else {
                    updateConnectionState(
                        BluetoothConnectionState.Error(
                            device,
                            "Failed to connect..."
                        )
                    )
                }

            } catch (e: Exception) {
                Timber.d("Failed to connect: ${e.message}")
                updateConnectionState(
                    BluetoothConnectionState.Error(
                        device,
                        "Failed to connect..."
                    )
                )
            }
        }
    }


    private suspend fun waitForConnection(
        context: Context,
        device: BluetoothDevice,
        timeout: Long = 10_000L
    ): Boolean {
        val result = CompletableDeferred<Boolean>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val state = intent?.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
                val prevState = intent?.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, -1)
                val targetDevice =
                    intent?.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)

                if (targetDevice == device && prevState == BluetoothProfile.STATE_CONNECTING) {
                    when (state) {
                        BluetoothProfile.STATE_CONNECTED -> result.complete(true)
                        BluetoothProfile.STATE_DISCONNECTED -> result.complete(false)
                    }
                }
            }
        }

        try {
            val filter = IntentFilter(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }

            return withTimeout(timeout) { result.await() }
        } catch (e: Exception) {
            Timber.e(e, "Error waiting for connection")
            return false
        } finally {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
        }
    }

    private fun createBondReceiver(context: Context, device: BluetoothDevice): BroadcastReceiver {
        return object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return

                val state =
                    intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                val prevState = intent.getIntExtra(
                    BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE,
                    BluetoothDevice.ERROR
                )

                when (state) {
                    BluetoothDevice.BOND_BONDED -> {
                        Timber.d("Pairing complete")
                        try {
                            ctx?.unregisterReceiver(this)
                        } catch (_: Exception) {
                        }
                        bondReceiver = null
                        updateConnectionState(BluetoothConnectionState.Connecting("Pairing successful"))
                        startConnection(context, device)
                    }

                    BluetoothDevice.BOND_BONDING -> {
                        Timber.d("Pairing in progress")
                        updateConnectionState(BluetoothConnectionState.Connecting("Pairing in progress..."))
                    }

                    BluetoothDevice.BOND_NONE -> if (prevState == BluetoothDevice.BOND_BONDING) {
                        Timber.e("Pairing failed")
                        updateConnectionState(
                            BluetoothConnectionState.Error(
                                device,
                                "Pairing failed"
                            )
                        )
                    }
                }
            }
        }
    }

    fun disconnect(
        device: BluetoothDevice
    ) {
        try {
            updateConnectionState(BluetoothConnectionState.Disconnecting)
            val method = device::class.java.getMethod("removeBond")
            method.invoke(device)
            updateConnectionState(BluetoothConnectionState.Disconnected(device))
        } catch (e: Exception) {
            updateConnectionState(
                BluetoothConnectionState.Error(
                    device,
                    "Failed to disconnect device..."
                )
            )
            Timber.e("Failed to disconnect device")
        }
    }

    fun clear() {
        connectionScope.cancel()
        bondReceiver = null
    }
}

