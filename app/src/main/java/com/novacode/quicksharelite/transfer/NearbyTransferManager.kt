package com.novacode.quicksharelite.transfer

import android.content.Context
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.novacode.quicksharelite.data.DiscoveredDevice
import com.novacode.quicksharelite.data.HistoryDao
import com.novacode.quicksharelite.data.HistoryEntity
import com.novacode.quicksharelite.data.LocalFile
import com.novacode.quicksharelite.data.TransferState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

class NearbyTransferManager(
    context: Context,
    private val fileStore: FileStore,
    private val historyDao: HistoryDao
) : NearbyTransportProvider.Callbacks {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val transport = NearbyTransportProvider(context.applicationContext, this)
    private val pendingPayloads = ConcurrentHashMap<Long, PendingPayload>()
    private val receivedPayloads = ConcurrentHashMap<Long, Payload>()
    private val connected = mutableSetOf<String>()

    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val devices: StateFlow<List<DiscoveredDevice>> = _devices

    private val _state = MutableStateFlow(TransferState.QUEUED)
    val state: StateFlow<TransferState> = _state
    private val _currentFileName = MutableStateFlow("")
    val currentFileName: StateFlow<String> = _currentFileName
    private val _bytesTransferred = MutableStateFlow(0L)
    val bytesTransferred: StateFlow<Long> = _bytesTransferred
    private val _totalBytes = MutableStateFlow(0L)
    val totalBytes: StateFlow<Long> = _totalBytes
    private val _speedBytesPerSecond = MutableStateFlow(0L)
    val speedBytesPerSecond: StateFlow<Long> = _speedBytesPerSecond
    private val _remainingSeconds = MutableStateFlow<Long?>(null)
    val remainingSeconds: StateFlow<Long?> = _remainingSeconds
    private val _pendingRequest = MutableStateFlow<IncomingRequest?>(null)
    val pendingRequest: StateFlow<IncomingRequest?> = _pendingRequest
    private val _verification = MutableStateFlow<VerificationRequest?>(null)
    val verification: StateFlow<VerificationRequest?> = _verification

    private var activeEndpoint: String? = null
    private var activeSession: String? = null
    private var activeFile: LocalFile? = null
    private var activeStagedFile: File? = null
    private var activeChecksum: String? = null
    private var activeIncomingRequest: IncomingRequest? = null
    private var transferStartedAt = 0L
    private var progressJob: Job? = null
    private var initiatingSend = false

    data class VerificationRequest(val endpointId: String, val token: String, val remoteName: String)
    data class IncomingRequest(val endpointId: String, val sessionId: String, val senderName: String, val fileName: String, val size: Long, val mime: String?, val sha256: String)
    private data class PendingPayload(val endpointId: String, val fileName: String, val size: Long, val sessionId: String, val sha256: String)

    init {
        scope.launch { transport.devices.collect { _devices.value = it } }
    }

    suspend fun startDiscovering() {
        _state.value = TransferState.DISCOVERING
        transport.stopAdvertising()
        transport.startDiscovery()
    }

    suspend fun stopDiscovering() = transport.stopDiscovery()

    suspend fun startReceiving(deviceName: String) {
        _state.value = TransferState.DISCOVERING
        transport.stopDiscovery()
        transport.startAdvertising(deviceName)
    }

    suspend fun stopReceiving() = transport.stopAdvertising()

    fun connect(endpoint: DiscoveredDevice, localName: String) {
        activeEndpoint = endpoint.endpointId
        initiatingSend = activeFile != null
        _state.value = TransferState.CONNECTING
        scope.launch {
            runCatching { transport.stopDiscovery(); transport.connect(endpoint.endpointId, localName) }
                .onFailure { _state.value = TransferState.FAILED }
        }
    }

    fun confirmVerification(accept: Boolean) {
        val request = _verification.value ?: return
        _verification.value = null
        scope.launch {
            if (accept) {
                transport.acceptConnection(request.endpointId)
                connected.add(request.endpointId)
            } else {
                transport.rejectConnection(request.endpointId)
                _state.value = TransferState.CANCELLED
            }
        }
    }

    fun rejectIncoming() {
        val request = _pendingRequest.value ?: return
        _pendingRequest.value = null
        activeIncomingRequest = null
        scope.launch { transport.sendBytes(request.endpointId, Protocol.command(Protocol.REJECT, request.sessionId)); transport.disconnect(request.endpointId) }
        _state.value = TransferState.CANCELLED
    }

    fun acceptIncoming() {
        val request = _pendingRequest.value ?: return
        activeIncomingRequest = request
        _pendingRequest.value = null
        scope.launch { transport.sendBytes(request.endpointId, Protocol.command(Protocol.ACCEPT, request.sessionId)) }
        _totalBytes.value = request.size
        _currentFileName.value = request.fileName
        _bytesTransferred.value = 0L
        _speedBytesPerSecond.value = 0L
        _remainingSeconds.value = null
        transferStartedAt = System.currentTimeMillis()
        _state.value = TransferState.ACCEPTED
    }

    fun prepareSend(localName: String, file: LocalFile) {
        activeSession = UUID.randomUUID().toString()
        activeFile = file
        activeEndpoint = null
        initiatingSend = false
        activeChecksum = null
        _totalBytes.value = file.size
        _currentFileName.value = file.name
        _bytesTransferred.value = 0L
        _speedBytesPerSecond.value = 0L
        _remainingSeconds.value = null
        _state.value = TransferState.DISCOVERING
        // The actual source is staged only after a real connection is established.
        pendingLocalName = localName
    }

    private var pendingLocalName = "Android Phone"

    fun cancelTransfer() {
        progressJob?.cancel()
        val endpoint = activeEndpoint
        if (endpoint != null) scope.launch { transport.disconnect(endpoint) }
        activeStagedFile?.let(fileStore::deleteQuietly)
        activeStagedFile = null
        activeIncomingRequest = null
        _state.value = TransferState.CANCELLED
    }

    override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
        _state.value = TransferState.AUTHENTICATING
        _verification.value = VerificationRequest(endpointId, info.authenticationToken, info.endpointName)
    }

    override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
        val code = result.status.statusCode
        if (code == ConnectionsStatusCodes.STATUS_OK) {
            connected.add(endpointId)
            if (initiatingSend && activeFile != null && activeEndpoint == endpointId) {
                _state.value = TransferState.WAITING_FOR_ACCEPT
                initiatingSend = false
                beginSendManifest(endpointId)
            }
        } else {
            _state.value = when (code) {
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> TransferState.CANCELLED
                else -> TransferState.FAILED
            }
        }
    }

    override fun onDisconnected(endpointId: String) {
        connected.remove(endpointId)
        if (_state.value != TransferState.COMPLETED && _state.value != TransferState.CANCELLED) _state.value = TransferState.CONNECTION_LOST
    }

    private fun beginSendManifest(endpointId: String) {
        val session = activeSession ?: return
        val file = activeFile ?: return
        scope.launch {
            try {
                activeStagedFile = fileStore.stageSource(file.uri, session)
                activeChecksum = fileStore.sha256(activeStagedFile!!)
                require(activeStagedFile!!.length() == file.size) { "Selected source changed while preparing" }
                transport.sendBytes(endpointId, Protocol.manifest(session, pendingLocalName, file.name, file.size, file.mimeType, activeChecksum!!))
            } catch (_: SecurityException) {
                _state.value = TransferState.PERMISSION_ERROR
            } catch (_: Exception) {
                _state.value = TransferState.FAILED
            }
        }
    }

    override fun onBytes(endpointId: String, bytes: ByteArray) {
        scope.launch {
            try {
                val obj = Protocol.parse(bytes)
                when (obj.optString("type")) {
                    Protocol.MANIFEST -> {
                        val file = obj.getJSONArray("files").getJSONObject(0)
                        _pendingRequest.value = IncomingRequest(
                            endpointId = endpointId,
                            sessionId = obj.getString("sessionId"),
                            senderName = obj.getString("senderName"),
                            fileName = file.getString("name"),
                            size = file.getLong("size"),
                            mime = file.optString("mime").ifBlank { null },
                            sha256 = file.getString("sha256")
                        )
                        _state.value = TransferState.WAITING_FOR_ACCEPT
                    }
                    Protocol.ACCEPT -> sendStagedFile(endpointId, obj.getString("sessionId"))
                    Protocol.REJECT -> {
                        activeStagedFile?.let(fileStore::deleteQuietly)
                        _state.value = TransferState.CANCELLED
                    }
                }
            } catch (_: Exception) {
                _state.value = TransferState.FAILED
            }
        }
    }

    private suspend fun sendStagedFile(endpointId: String, sessionId: String) {
        if (sessionId != activeSession) return
        val source = activeStagedFile ?: return
        val original = activeFile ?: return
        _state.value = TransferState.TRANSFERRING
        transferStartedAt = System.currentTimeMillis()
        transport.sendFile(endpointId, original, source.absolutePath) { _, _ -> }
    }

    override fun onFile(endpointId: String, payload: Payload) {
        receivedPayloads[payload.id] = payload
        val request = activeIncomingRequest
        if (request != null && request.endpointId == endpointId) {
            pendingPayloads[payload.id] = PendingPayload(endpointId, request.fileName, request.size, request.sessionId, request.sha256)
        }
    }

    override fun onPayloadProgress(endpointId: String, payloadId: Long, bytes: Long, total: Long, status: Int) {
        if (_state.value != TransferState.TRANSFERRING && status == PayloadTransferUpdate.Status.IN_PROGRESS) {
            _state.value = TransferState.TRANSFERRING
        }
        _bytesTransferred.value = bytes
        _totalBytes.value = max(total, 0L)
        if (transferStartedAt > 0L && bytes > 0L) {
            val elapsedMs = max(1L, System.currentTimeMillis() - transferStartedAt)
            val speed = bytes * 1000L / elapsedMs
            _speedBytesPerSecond.value = speed
            _remainingSeconds.value = if (total > bytes && speed > 0) (total - bytes) / speed else null
        }
        if (status == PayloadTransferUpdate.Status.SUCCESS) {
            val received = receivedPayloads.remove(payloadId)
            val incomingMeta = pendingPayloads.remove(payloadId)
            if (received != null && incomingMeta != null) {
                scope.launch {
                    runCatching {
                        val incoming = received.asFile() ?: error("Nearby file payload was empty")
                        require(incoming.length() == incomingMeta.size) { "Received size mismatch" }
                        require(fileStore.sha256(incoming).equals(incomingMeta.sha256, ignoreCase = true)) { "Checksum mismatch" }
                        val target = fileStore.destination(incomingMeta.fileName)
                        val partial = File(target.parentFile, "${target.name}.partial")
                        incoming.copyTo(partial, overwrite = true)
                        require(partial.length() == incomingMeta.size) { "Destination write truncated" }
                        fileStore.completePartial(partial, target)
                        _bytesTransferred.value = target.length()
                        _totalBytes.value = incomingMeta.size
                        _state.value = TransferState.COMPLETED
                        activeIncomingRequest = null
                        historyDao.insert(HistoryEntity(System.currentTimeMillis(), "RECEIVED", incomingMeta.endpointId, 1, incomingMeta.size, "COMPLETED"))
                    }.onFailure { _state.value = TransferState.STORAGE_ERROR }
                }
            } else {
                _state.value = TransferState.COMPLETED
                activeStagedFile?.let(fileStore::deleteQuietly)
                activeStagedFile = null
                activeChecksum = null
                scope.launch { historyDao.insert(HistoryEntity(System.currentTimeMillis(), "SENT", "Nearby device", 1, total, "COMPLETED")) }
            }
        } else if (status == PayloadTransferUpdate.Status.FAILURE) {
            _state.value = TransferState.FAILED
        } else if (status == PayloadTransferUpdate.Status.CANCELED) {
            _state.value = TransferState.CANCELLED
        }
    }
}
