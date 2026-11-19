package com.example.kiseh.data.local.entity

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface IgnoredSmsDao {
    @Query("SELECT smsBody FROM ignored_sms")
    suspend fun getAllIgnoredSmsBodies(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIgnoredSms(ignoredSms: IgnoredSmsEntity)
}
