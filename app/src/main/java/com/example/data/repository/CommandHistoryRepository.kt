package com.example.data.repository

import com.example.data.database.CommandHistoryDao
import com.example.data.database.CommandHistoryEntity
import kotlinx.coroutines.flow.Flow

class CommandHistoryRepository(private val dao: CommandHistoryDao) {
    val allHistory: Flow<List<CommandHistoryEntity>> = dao.getAllHistory()

    suspend fun insert(entity: CommandHistoryEntity) {
        dao.insert(entity)
    }

    suspend fun clearAll() {
        dao.clearAll()
    }
}
