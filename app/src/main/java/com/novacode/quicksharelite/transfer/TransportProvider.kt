package com.novacode.quicksharelite.transfer

import com.novacode.quicksharelite.data.DiscoveredDevice
import com.novacode.quicksharelite.data.LocalFile
import kotlinx.coroutines.flow.StateFlow

interface TransportProvider {
    val devices: StateFlow<List<DiscoveredDevice>>
    suspend fun startAdvertising(deviceName: String)
    suspend fun stopAdvertising()
    suspend fun startDiscovery()
    suspend fun stopDiscovery()
    suspend fun connect(endpointId: String, localName: String)
    suspend fun acceptConnection(endpointId: String)
    suspend fun rejectConnection(endpointId: String)
    suspend fun sendBytes(endpointId: String, bytes: ByteArray)
    suspend fun sendFile(endpointId: String, file: LocalFile, tempFilePath: String, onProgress: (Long, Long) -> Unit)
    suspend fun disconnect(endpointId: String)
}
