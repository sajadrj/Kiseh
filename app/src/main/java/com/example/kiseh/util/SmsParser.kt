package com.example.kiseh.util

data class ParsedTransaction(
    val title: String,
    val amount: Double,
    val type: String, // "INCOME" یا "EXPENSE"
    val rawMessage: String
)

object SmsParser {

    // یک الگوی عمومی برای تشخیص واریز یا برداشت پیامک‌های بانکی
    fun parseSms(messageBody: String): ParsedTransaction? {
        // بررسی اینکه آیا پیامک مربوط به تراکنش بانکی است یا خیر
        val isExpense = messageBody.contains("برداشت", ignoreCase = true) ||
                messageBody.contains("خرید", ignoreCase = true) ||
                messageBody.contains("انتقال وجه", ignoreCase = true)

        val isIncome = messageBody.contains("واریز", ignoreCase = true) ||
                messageBody.contains("سپاس", ignoreCase = true)

        if (!isExpense && !isIncome) return null

        // استخراج مبلغ با استفاده از Regex (اعداد و کاماها/ارقام فارسی)
        // این یک الگوی ساده برای شروع است که مقادیر را پیدا می‌کند
        val amountRegex = Regex("([0-9۰-۹,]+)\\s*(ریال|تومان)")
        val matchResult = amountRegex.find(messageBody)

        if (matchResult == null) return null

        val amountStr = matchResult.groupValues[1]
            .replace(",", "")
            .replace("۰", "0").replace("۱", "1").replace("۲", "2")
            .replace("۳", "3").replace("۴", "4").replace("۵", "5")
            .replace("۶", "6").replace("۷", "7").replace("۸", "8").replace("۹", "9")

        val amount = amountStr.toDoubleOrNull() ?: 0.0
        if (amount <= 0.0) return null

        val type = if (isIncome) "INCOME" else "EXPENSE"
        val title = if (isIncome) "واریز به حساب" else "خرید / برداشت"

        return ParsedTransaction(
            title = title,
            amount = amount,
            type = type,
            rawMessage = messageBody
        )
    }
}