package com.example.kiseh.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.kiseh.data.local.entity.AppDatabase
import com.example.kiseh.data.local.entity.TransactionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val messageBody = sms.messageBody ?: continue
                val sender = sms.originatingAddress ?: ""

                // تحلیل پیامک با استفاده از پارسری که نوشتیم
                val parsed = SmsParser.parseSms(messageBody)
                if (parsed != null) {
                    // ذخیره خودکار تراکنش در دیتابیس در پس‌زمینه
                    val database = AppDatabase.getDatabase(context)
                    val transactionDao = database.transactionDao()

                    CoroutineScope(Dispatchers.IO).launch {
                        val transaction = TransactionEntity(
                            title = "${parsed.title} ($sender)",
                            amount = parsed.amount,
                            type = parsed.type,
                            category = "عمومی",
                            date = System.currentTimeMillis(),
                            smsBody = parsed.rawMessage
                        )
                        transactionDao.insertTransaction(transaction)
                    }
                }
            }
        }
    }
}
