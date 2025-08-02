package dev.ajitgoud.bluetoothheadset

import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ajitgoud.bluetoothheadset.bluetooth.BluetoothConnectClient
import dev.ajitgoud.bluetoothheadset.bluetooth.BluetoothDiscoveryClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BluetoothScreenViewModel @Inject constructor(
    private val discoveryClient: BluetoothDiscoveryClient,
    private val connectClient: BluetoothConnectClient
) : ViewModel() {

    private val _discoveredDevices = MutableStateFlow<Set<BluetoothDevice>>(emptySet())
    val discoveredDevices: StateFlow<Set<BluetoothDevice>> = _discoveredDevices

    val connectionState = connectClient.connectionStateFlow

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering

    private var lastDiscoveryJob: Job? = null

    init {
        observeDiscovery()
    }

    private fun observeDiscovery() {
        lastDiscoveryJob = viewModelScope.launch {
            discoveryClient.discoveryState.collect { state ->
                when (state) {
                    is BluetoothDiscoveryClient.BluetoothDiscoveryState.DiscoveryStarted -> {
                        _isDiscovering.value = true
                        _discoveredDevices.value = emptySet()
                    }

                    is BluetoothDiscoveryClient.BluetoothDiscoveryState.DiscoveryStopped -> {
                        _isDiscovering.value = false
                    }

                    is BluetoothDiscoveryClient.BluetoothDiscoveryState.BluetoothDeviceFound -> {
                        _discoveredDevices.value += state.device
                    }

                    is BluetoothDiscoveryClient.BluetoothDiscoveryState.Error -> {
                        _isDiscovering.value = false
                    }
                }
            }
        }
    }

    fun toggleDiscovery(context: Context) {
        if (_isDiscovering.value) {
            stopDiscovery(context)
        } else {
            startDiscovery(context)
        }
    }

    private fun startDiscovery(context: Context) {
        discoveryClient.startDiscovery(context)
    }

    private fun stopDiscovery(context: Context) {
        discoveryClient.stopDiscovery(context)
    }

    fun connectToDevice(context: Context, device: BluetoothDevice) {
        stopDiscovery(context)
        connectClient.connect(context, device)
    }

    fun disconnectDevice(device: BluetoothDevice) {
        connectClient.disconnect(device)
    }

    override fun onCleared() {
        super.onCleared()
        connectClient.clear()
        lastDiscoveryJob?.cancel()
    }
}
