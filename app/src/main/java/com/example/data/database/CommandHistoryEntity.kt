package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "command_history")
data class CommandHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val command: String,
    val response: String,
    val error: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
