package com.example.kiseh.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val amount: Double,
    val type: String, // "INCOME" یا "EXPENSE"
    val category: String,
    val date: Long,
    val smsBody: String? = null
)