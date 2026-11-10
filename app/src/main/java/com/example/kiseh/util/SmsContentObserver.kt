package com.example.kiseh.util

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import android.widget.Toast
import com.example.kiseh.data.local.entity.AppDatabase
import com.example.kiseh.data.local.entity.TransactionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsContentObserver(
    private val context: Context,
    handler: Handler = Handler(Looper.getMainLooper())
) : ContentObserver(handler) {

    private var lastProcessedSmsId: Long = -1L

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        Log.e("KisehSMS", "===> ContentObserver onChange فراخوانی شد. Uri: $uri")

        try {
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE
            )

            // استعلام آخرین پیامک دریافتی در صندوق ورودی
            val cursor = context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC LIMIT 1"
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val idIndex = it.getColumnIndex(Telephony.Sms._ID)
                    val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
                    val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)

                    val smsId = if (idIndex != -1) it.getLong(idIndex) else -1L
                    val sender = if (addressIndex != -1) it.getString(addressIndex) ?: "ناشناس" else "ناشناس"
                    val body = if (bodyIndex != -1) it.getString(bodyIndex) ?: "" else ""

                    if (smsId != -1L && smsId != lastProcessedSmsId) {
                        lastProcessedSmsId = smsId
                        Log.e("KisehSMS", "📩 پیامک جدید از طریق ContentObserver دریافت شد!")
                        Log.e("KisehSMS", "فرستنده: $sender | متن: $body")

                        showToast("📩 پیامک جدید از $sender خوانده شد")

                        CoroutineScope(Dispatchers.IO).launch {
                            val parsed = SmsParser.parseSms(body, sender)
                            if (parsed != null) {
                                Log.e("KisehSMS", "✅ تراکنش شناسایی شد: ${parsed.amount}")
                                val database = AppDatabase.getDatabase(context)
                                val transactionDao = database.transactionDao()

                                val transaction = TransactionEntity(
                                    title = parsed.title,
                                    amount = parsed.amount,
                                    type = parsed.type,
                                    category = parsed.category,
                                    bankName = parsed.bankName,
                                    date = System.currentTimeMillis(),
                                    smsBody = parsed.rawMessage
                                )
                                transactionDao.insertTransaction(transaction)
                                showToast("✅ تراکنش ${parsed.amount} ریالی ذخیره شد!")
                            } else {
                                Log.e("KisehSMS", "⚠️ پیامک دریافت شد اما الگوی تراکنش بانکی در آن یافت نشد.")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("KisehSMS", "❌ خطا در ContentObserver", e)
        }
    }

    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        fun syncAllBankSms(context: Context, onComplete: (Int) -> Unit) {
            CoroutineScope(Dispatchers.IO).launch {
                var addedCount = 0
                try {
                    val projection = arrayOf(
                        Telephony.Sms._ID,
                        Telephony.Sms.ADDRESS,
                        Telephony.Sms.BODY,
                        Telephony.Sms.DATE
                    )

                    val cursor = context.contentResolver.query(
                        Telephony.Sms.Inbox.CONTENT_URI,
                        projection,
                        null,
                        null,
                        "${Telephony.Sms.DATE} DESC LIMIT 50"
                    )

                    val database = AppDatabase.getDatabase(context)
                    val transactionDao = database.transactionDao()

                    cursor?.use {
                        val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
                        val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
                        val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)

                        while (it.moveToNext()) {
                            val sender = if (addressIndex != -1) it.getString(addressIndex) ?: "ناشناس" else "ناشناس"
                            val body = if (bodyIndex != -1) it.getString(bodyIndex) ?: "" else ""
                            val date = if (dateIndex != -1) it.getLong(dateIndex) else System.currentTimeMillis()

                            val parsed = SmsParser.parseSms(body, sender)
                            if (parsed != null) {
                                transactionDao.insertTransaction(
                                    TransactionEntity(
                                        title = parsed.title,
                                        amount = parsed.amount,
                                        type = parsed.type,
                                        category = parsed.category,
                                        bankName = parsed.bankName,
                                        date = date,
                                        smsBody = parsed.rawMessage
                                    )
                                )
                                addedCount++
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("KisehSMS", "خطا در همگام‌سازی پیامک‌ها", e)
                }
                Handler(Looper.getMainLooper()).post {
                    onComplete(addedCount)
                }
            }
        }
    }
}
