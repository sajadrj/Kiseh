package com.example.kiseh.util

import android.content.Context
import android.net.Uri
import com.example.kiseh.data.local.entity.TransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

class BackupManager(private val context: Context) {

    // تبدیل لیست تراکنش‌ها به JSON و نوشتن در فایل خروجی
    suspend fun exportBackup(uri: Uri, transactions: List<TransactionEntity>): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val jsonArray = JSONArray()
                for (tx in transactions) {
                    val obj = JSONObject().apply {
                        put("id", tx.id)
                        put("title", tx.title)
                        put("amount", tx.amount)
                        put("type", tx.type)
                        put("category", tx.category)
                        put("bankName", tx.bankName)
                        put("date", tx.date)
                    }
                    jsonArray.put(obj)
                }

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonArray.toString(2).toByteArray(Charsets.UTF_8))
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    // خواندن فایل JSON و تبدیل مجدد به لیست تراکنش‌ها
    suspend fun importBackup(uri: Uri): List<TransactionEntity>? {
        return withContext(Dispatchers.IO) {
            try {
                val stringBuilder = StringBuilder()
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                        var line = reader.readLine()
                        while (line != null) {
                            stringBuilder.append(line)
                            line = reader.readLine()
                        }
                    }
                }

                val jsonArray = JSONArray(stringBuilder.toString())
                val restoredList = mutableListOf<TransactionEntity>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    restoredList.add(
                        TransactionEntity(
                            id = if (obj.has("id")) obj.getLong("id") else 0,
                            title = obj.optString("title", "بدون عنوان"),
                            amount = obj.optDouble("amount", 0.0),
                            type = obj.optString("type", "EXPENSE"),
                            category = obj.optString("category", "متفرقه"),
                            bankName = obj.optString("bankName", "سایر"),
                            date = obj.optLong("date", System.currentTimeMillis())
                        )
                    )
                }
                restoredList
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}