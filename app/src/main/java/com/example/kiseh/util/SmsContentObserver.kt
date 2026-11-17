package com.example.kiseh.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.kiseh.data.local.entity.AppDatabase
import com.example.kiseh.data.local.entity.TransactionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs

class SmsContentObserver(
    private val context: Context,
    handler: Handler = Handler(Looper.getMainLooper())
) : ContentObserver(handler) {

    private var lastProcessedSmsId: Long = -1L

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        Log.e("KisehSMS", "===> ContentObserver onChange فراخوانی شد. Uri: $uri")

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.e("KisehSMS", "❌ مجوز READ_SMS صادر نشده است.")
            return
        }

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
                            val database = AppDatabase.getDatabase(context)
                            val ignoredBodies = database.ignoredSmsDao().getAllIgnoredSmsBodies()
                            if (ignoredBodies.contains(body)) {
                                Log.e("KisehSMS", "⚠️ این پیامک قبلاً توسط کاربر حذف شده و نادیده گرفته شد.")
                                return@launch
                            }

                            val parsed = SmsParser.parseSms(body, sender)
                            if (parsed != null) {
                                Log.e("KisehSMS", "✅ تراکنش شناسایی شد: ${parsed.amount}")
                                val transactionDao = database.transactionDao()
                                val existing = transactionDao.getAllTransactionsList()

                                val isDuplicate = existing.any { tx ->
                                    (tx.smsBody != null && tx.smsBody == body) ||
                                            (tx.amount == parsed.amount && tx.title == parsed.title && abs(tx.date - System.currentTimeMillis()) < 120000L)
                                }

                                if (!isDuplicate) {
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
                                    Log.e("KisehSMS", "⚠️ تراکنش تکراری است و ذخیره نشد.")
                                }
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
        fun syncAllBankSms(context: Context, forceOneWeek: Boolean = false, onComplete: (Int, Boolean) -> Unit = { _, _ -> }) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "⚠️ مجوز خواندن پیامک‌ها (READ_SMS) صادر نشده است.", Toast.LENGTH_LONG).show()
                    onComplete(0, false)
                }
                return
            }

            CoroutineScope(Dispatchers.IO).launch {
                var addedCount = 0
                var isFirstTime = false
                try {
                    val database = AppDatabase.getDatabase(context)
                    val transactionDao = database.transactionDao()
                    val ignoredBodies = database.ignoredSmsDao().getAllIgnoredSmsBodies()
                    val existing = transactionDao.getAllTransactionsList()
                    val budgetManager = BudgetManager(context)

                    // اگر درخواست دستی (forceOneWeek) باشد یا دیتابیس خالی باشد، یک هفته گذشته؛ وگرنه از آخرین تراکنش
                    val sinceTimestamp = if (forceOneWeek || existing.isEmpty()) {
                        isFirstTime = existing.isEmpty()
                        System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
                    } else {
                        budgetManager.getOrSetFirstLaunchTime()
                    }

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
                        "${Telephony.Sms.DATE} DESC LIMIT 500"
                    )

                    cursor?.use {
                        val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
                        val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
                        val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)

                        Log.d("KisehSMS", "تعداد کل پیامک‌های یافت شده در اینباکس برای همگام‌سازی: ${it.count} | مبنای زمان: $sinceTimestamp")

                        while (it.moveToNext()) {
                            val sender = if (addressIndex != -1) it.getString(addressIndex) ?: "ناشناس" else "ناشناس"
                            val body = if (bodyIndex != -1) it.getString(bodyIndex) ?: "" else ""
                            val date = if (dateIndex != -1) it.getLong(dateIndex) else System.currentTimeMillis()

                            // اگر پیامک قدیمی‌تر از زمان مبنا است، رد کن
                            if (date < sinceTimestamp) {
                                continue
                            }

                            if (ignoredBodies.contains(body)) {
                                Log.d("KisehSMS", "⚠️ پیامک در لیست سیاه (حذف‌شده) قرار دارد و همگام‌سازی نمی‌شود: $body")
                                continue
                            }

                            val parsed = SmsParser.parseSms(body, sender)
                            if (parsed != null) {
                                val isDuplicate = existing.any { tx ->
                                    (tx.smsBody != null && tx.smsBody == body) ||
                                            (tx.amount == parsed.amount && tx.title == parsed.title && abs(tx.date - date) < 120000L)
                                }

                                if (!isDuplicate) {
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
                                    Log.d("KisehSMS", "✅ تراکنش همگام‌سازی شد: ${parsed.amount} از فرستنده $sender")
                                } else {
                                    Log.d("KisehSMS", "⚠️ تراکنش تکراری رد شد: ${parsed.amount}")
                                }
                            } else {
                                Log.d("KisehSMS", "❌ پیامک فاقد الگوی تراکنش: $body")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("KisehSMS", "خطا در همگام‌سازی پیامک‌ها", e)
                }
                Handler(Looper.getMainLooper()).post {
                    onComplete(addedCount, isFirstTime)
                }
            }
        }
    }
}
