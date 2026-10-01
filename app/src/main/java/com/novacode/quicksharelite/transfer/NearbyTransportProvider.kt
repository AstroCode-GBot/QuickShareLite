package com.novacode.quicksharelite.transfer

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.novacode.quicksharelite.data.DiscoveredDevice
import com.novacode.quicksharelite.data.LocalFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import java.io.File

class NearbyTransportProvider(
    private val context: Context,
    private val callbacks: Callbacks
) : TransportProvider {
    companion object {
        private const val SERVICE_ID = "com.novacode.quicksharelite"
        private val STRATEGY = Strategy.P2P_POINT_TO_POINT
    }

    interface Callbacks {
        fun onConnectionInitiated(endpointId: String, info: ConnectionInfo)
        fun onConnectionResult(endpointId: String, result: ConnectionResolution)
        fun onDisconnected(endpointId: String)
        fun onBytes(endpointId: String, bytes: ByteArray)
        fun onFile(endpointId: String, payload: Payload)
        fun onPayloadProgress(endpointId: String, payloadId: Long, bytes: Long, total: Long, status: Int)
    }

    private val client by lazy { Nearby.getConnectionsClient(context) }
    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    override val devices: StateFlow<List<DiscoveredDevice>> = _devices

    private val discovered = linkedMapOf<String, DiscoveredDevice>()

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            discovered[endpointId] = DiscoveredDevice(endpointId, info.endpointName)
            _devices.value = discovered.values.toList()
            callbacks // keep callback object lifecycle explicit
        }
        override fun onEndpointLost(endpointId: String) {
            discovered.remove(endpointId)
            _devices.value = discovered.values.toList()
        }
    }

    private val connectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) =
            callbacks.onConnectionInitiated(endpointId, connectionInfo)

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) =
            callbacks.onConnectionResult(endpointId, result)

        override fun onDisconnected(endpointId: String) = callbacks.onDisconnected(endpointId)
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> payload.asBytes()?.let { callbacks.onBytes(endpointId, it) }
                Payload.Type.FILE -> callbacks.onFile(endpointId, payload)
                Payload.Type.STREAM -> Unit // v1 sends FILE payloads, not unbounded streams.
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            callbacks.onPayloadProgress(endpointId, update.payloadId, update.bytesTransferred, update.totalBytes, update.status)
        }
    }

    override suspend fun startAdvertising(deviceName: String) {
        client.startAdvertising(
            deviceName,
            SERVICE_ID,
            connectionCallback,
            AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        ).await()
    }

    override suspend fun stopAdvertising() { runCatching { client.stopAdvertising().await() } }

    override suspend fun startDiscovery() {
        client.startDiscovery(
            SERVICE_ID,
            discoveryCallback,
            DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        ).await()
    }

    override suspend fun stopDiscovery() {
        runCatching { client.stopDiscovery().await() }
        discovered.clear()
        _devices.value = emptyList()
    }

    override suspend fun connect(endpointId: String, localName: String) {
        client.requestConnection(localName, endpointId, connectionCallback).await()
    }

    override suspend fun acceptConnection(endpointId: String) { client.acceptConnection(endpointId, payloadCallback).await() }
    override suspend fun rejectConnection(endpointId: String) { client.rejectConnection(endpointId).await() }
    override suspend fun sendBytes(endpointId: String, bytes: ByteArray) { client.sendPayload(endpointId, Payload.fromBytes(bytes)).await() }

    override suspend fun sendFile(endpointId: String, file: LocalFile, tempFilePath: String, onProgress: (Long, Long) -> Unit) {
        val payloadFile = File(tempFilePath)
        client.sendPayload(endpointId, Payload.fromFile(payloadFile)).await()
        // Detailed progress arrives through callbacks; this callback keeps the abstraction ready for queue-level telemetry.
        onProgress(0L, file.size)
    }

    override suspend fun disconnect(endpointId: String) { runCatching { client.disconnectFromEndpoint(endpointId).await() } }
}
