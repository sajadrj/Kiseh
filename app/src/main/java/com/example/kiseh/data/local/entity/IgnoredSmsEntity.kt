package com.example.kiseh.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ignored_sms")
data class IgnoredSmsEntity(
    @PrimaryKey
    val smsBody: String
)
