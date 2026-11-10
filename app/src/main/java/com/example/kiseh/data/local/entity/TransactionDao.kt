package com.example.kiseh.data.local.entity

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class CategorySummary(
    val category: String,
    val type: String,
    val totalAmount: Double
)

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE title LIKE '%' || :query || '%' OR CAST(amount AS TEXT) LIKE '%' || :query || '%' ORDER BY date DESC")
    fun searchTransactions(query: String): Flow<List<TransactionEntity>>

    @Query("SELECT category, type, SUM(amount) as totalAmount FROM transactions GROUP BY category, type")
    fun getSummaryByCategory(): Flow<List<CategorySummary>>

    @Query("SELECT category, type, SUM(amount) as totalAmount FROM transactions WHERE type = :type GROUP BY category, type ORDER BY totalAmount DESC")
    fun getSummaryByCategoryAndType(type: String): Flow<List<CategorySummary>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :transactionId")
    suspend fun deleteTransactionById(transactionId: Long)
}