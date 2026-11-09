package com.example.kiseh.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("KisehSMS", "==============================================")
        Log.d("KisehSMS", "رویداد onReceive فراخوانی شد. Action: ${intent.action}")

        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val pendingResult = goAsync()
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

            if (messages.isNullOrEmpty()) {
                Log.w("KisehSMS", "لیست پیامک‌ها خالی است!")
                pendingResult.finish()
                return
            }

            val fullBody = messages.joinToString(separator = "") { it.messageBody ?: "" }
            val sender = messages.firstOrNull()?.originatingAddress ?: "ناشناس"

            Log.d("KisehSMS", "پیامک جدید دریافت شد!")
            Log.d("KisehSMS", "فرستنده: $sender")
            Log.d("KisehSMS", "متن پیامک: $fullBody")

            // نمایش Toast مستقیم روی صفحه گوشی جهت اطمینان کاربر
            showToast(context, "📩 پیامک دریافت شد از: $sender")

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val parsed = SmsParser.parseSms(fullBody, sender)
                    if (parsed != null) {
                        Log.d("KisehSMS", "✅ تراکنش با موفقیت شناسایی شد: عنوان=${parsed.title} | مبلغ=${parsed.amount} | نوع=${parsed.type}")

                        val database = AppDatabase.getDatabase(context)
                        val transactionDao = database.transactionDao()

                        val transaction = TransactionEntity(
                            title = parsed.title,
                            amount = parsed.amount,
                            type = parsed.type,
                            category = "پیامک خودکار",
                            bankName = parsed.bankName,
                            date = System.currentTimeMillis(),
                            smsBody = parsed.rawMessage
                        )
                        transactionDao.insertTransaction(transaction)
                        Log.d("KisehSMS", "💾 تراکنش با موفقیت در دیتابیس ذخیره شد.")

                        showToast(context, "✅ تراکنش ${parsed.amount} ریالی ذخیره شد!")
                    } else {
                        Log.w("KisehSMS", "⚠️ پیامک دریافت شد اما الگوی تراکنش بانکی در آن پیدا نشد.")
                        showToast(context, "⚠️ پیامک بانکی تشخیص داده نشد.")
                    }
                } catch (e: Exception) {
                    Log.e("KisehSMS", "❌ خطا در پردازش یا ذخیره‌سازی پیامک", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun showToast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }
}
