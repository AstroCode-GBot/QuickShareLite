package com.novacode.quicksharelite.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transfer_history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val direction: String,
    val device: String,
    val fileCount: Int,
    val totalBytes: Long,
    val status: String,
    val durationMs: Long = 0,
    val averageBytesPerSecond: Long = 0
)
