package dev.ajitgoud.bluetoothheadset.di

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Singleton
    @Provides
    fun provideBluetoothManager(@ApplicationContext context: Context): BluetoothManager =
        context.getSystemService(
            BluetoothManager::class.java
        )


    @Singleton
    @Provides
    fun providesBluetoothAdapter(bluetoothManager: BluetoothManager): BluetoothAdapter =
        bluetoothManager.adapter
}