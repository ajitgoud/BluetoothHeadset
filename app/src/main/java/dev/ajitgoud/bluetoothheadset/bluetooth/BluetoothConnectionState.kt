package dev.ajitgoud.bluetoothheadset.bluetooth

import android.bluetooth.BluetoothDevice

sealed interface BluetoothConnectionState {
    data object Idle : BluetoothConnectionState
    data class Connecting(val message: String) : BluetoothConnectionState
    data object Disconnecting : BluetoothConnectionState
    data class Connected(val device: BluetoothDevice) : BluetoothConnectionState
    data class Disconnected(val device: BluetoothDevice) : BluetoothConnectionState
    data class Error(val device: BluetoothDevice, val message: String) : BluetoothConnectionState
}