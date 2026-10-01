package com.novacode.quicksharelite.data

import android.net.Uri

data class Profile(
    val name: String,
    val avatar: Int = 0
)

data class LocalFile(
    val uri: Uri,
    val name: String,
    val size: Long,
    val mimeType: String?
)

data class DiscoveredDevice(
    val endpointId: String,
    val name: String
)

enum class TransferState {
    QUEUED, DISCOVERING, CONNECTING, AUTHENTICATING, WAITING_FOR_ACCEPT,
    ACCEPTED, TRANSFERRING, RETRYING, COMPLETED, FAILED, CANCELLED,
    CONNECTION_LOST, STORAGE_ERROR, PERMISSION_ERROR
}
