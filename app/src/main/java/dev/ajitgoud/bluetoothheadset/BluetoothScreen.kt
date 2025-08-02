package dev.ajitgoud.bluetoothheadset

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.ajitgoud.bluetoothheadset.bluetooth.BluetoothConnectionState

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothScreen(
    viewModel: BluetoothScreenViewModel
) {
    val isDiscovering by viewModel.isDiscovering.collectAsState()
    val devices by viewModel.discoveredDevices.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Bluetooth Demo") })
        }
    ) { padding ->
        Column(
            modifier = Modifier.run {
                fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            }
        ) {
            when (connectionState) {
                is BluetoothConnectionState.Idle -> {
                    Text("Idle", color = Color.Gray)
                }

                is BluetoothConnectionState.Connecting -> {
                    val msg = (connectionState as BluetoothConnectionState.Connecting).message
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Connecting: $msg")
                    }
                }

                is BluetoothConnectionState.Connected -> {
                    val device = (connectionState as BluetoothConnectionState.Connected).device
                    Text("Connected to: ${device.name ?: device.address}")
                    Button(onClick = { viewModel.disconnectDevice(device) }) {
                        Text("Disconnect")
                    }
                }

                is BluetoothConnectionState.Disconnecting -> {
                    Text("Disconnecting...")
                }

                is BluetoothConnectionState.Disconnected -> {
                    val device = (connectionState as BluetoothConnectionState.Disconnected).device
                    Text("Disconnected from: ${device.name ?: device.address}")
                }

                is BluetoothConnectionState.Error -> {
                    val err = (connectionState as BluetoothConnectionState.Error)
                    Text("Error: ${err.message}", color = Color.Red)
                    Row {
                        Button(onClick = { viewModel.connectToDevice(context, err.device) }) {
                            Text("Retry")
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { }) {
                            Text("Cancel")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.toggleDiscovery(context) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDiscovering) Color.Red else Color.Green
                )
            ) {
                Text(if (isDiscovering) "Stop Discovery" else "Start Discovery")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Found Devices:", style = MaterialTheme.typography.titleMedium)
            LazyColumn {
                items(devices.toList()) { device ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(device.name ?: "Unknown Device")
                                Text(device.address, style = MaterialTheme.typography.bodySmall)
                            }
                            Button(
                                onClick = { viewModel.connectToDevice(context, device) },
                                enabled = connectionState !is BluetoothConnectionState.Connecting
                            ) {
                                Text("Connect")
                            }
                        }
                    }
                }
            }
        }
    }
}
