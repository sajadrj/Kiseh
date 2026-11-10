package com.example.kiseh.util

data class ParsedTransaction(
    val title: String,
    val amount: Double,
    val type: String, // "INCOME" یا "EXPENSE"
    val category: String,
    val bankName: String,
    val rawMessage: String
)

object SmsParser {

    fun parseSms(messageBody: String, sender: String = ""): ParsedTransaction? {
        if (messageBody.isBlank()) return null

        // نرمال‌سازی حروف عربی (ك، ي، ى) به فارسی (ک، ی) جهت تطابق کامل
        val normalizedBody = messageBody
            .replace('ك', 'ک')
            .replace('ي', 'ی')
            .replace('ى', 'ی')
        val normalizedSender = sender
            .replace('ك', 'ک')
            .replace('ي', 'ی')
            .replace('ى', 'ی')

        val text = normalizedBody.lowercase()
        val senderLower = normalizedSender.lowercase()
        val isSenderNumeric = normalizedSender.all { it.isDigit() || it == '+' || it == '-' || it == ' ' }

        // تشخیص دقیق نام بانک (بررسی متن پیامک و در صورت نیاز نام فرستنده)
        val bankName = when {
            // ۱. بررسی متن پیامک (اولویت اول برای تشخیص نام بانک از داخل متن)
            text.contains("بانک ملی") || text.contains("ملی ایران") || text.contains("bankmelli") -> "بانک ملی"
            text.contains("بانک ملت") || text.contains("bankmellat") -> "بانک ملت"
            text.contains("بانک صادرات") -> "بانک صادرات"
            text.contains("بانک سپه") -> "بانک سپه"
            text.contains("بانک تجارت") || text.contains("tejarat") -> "بانک تجارت"
            text.contains("بانک پاسارگاد") || text.contains("pasargad") -> "بانک پاسارگاد"
            text.contains("بانک سامان") || text.contains("saman") -> "بانک سامان"
            text.contains("بانک کشاورزی") || text.contains("keshavarzi") -> "بانک کشاورزی"
            text.contains("بانک مسکن") || text.contains("maskan") -> "بانک مسکن"
            text.contains("بانک رفاه") || text.contains("refah") -> "بانک رفاه کارگران"
            text.contains("بانک شهر") || text.contains("shahr") -> "بانک شهر"
            text.contains("بانک دی") || text.contains("dey") -> "بانک دی"
            text.contains("بانک آینده") || text.contains("ayandeh") -> "بانک آینده"
            text.contains("بانک رسالت") || text.contains("resalat") -> "بانک رسالت"
            text.contains("بانک مهر") || text.contains("مهر ایران") || text.contains("mehr") -> "بانک مهر ایران"
            text.contains("بانک پارسیان") || text.contains("parsian") -> "بانک پارسیان"
            text.contains("اقتصاد نوین") -> "بانک اقتصاد نوین"
            text.contains("سینا") -> "بانک سینا"
            text.contains("کارآفرین") -> "بانک کارآفرین"
            text.contains("گردشگری") -> "بانک گردشگری"
            text.contains("پست بانک") -> "پست بانک ایران"
            text.contains("بلو بانک") || text.contains("بلوبانک") || text.contains("blubank") || text.contains("بلو") -> "بلو بانک"

            // ۲. بررسی نام فرستنده (اگر سرشماره متن باشد یا نام بانک در فرستنده ذکر شده باشد)
            !isSenderNumeric && (senderLower.contains("melli") || senderLower.contains("ملی")) -> "بانک ملی"
            !isSenderNumeric && (senderLower.contains("mellat") || senderLower.contains("ملت")) -> "بانک ملت"
            !isSenderNumeric && (senderLower.contains("saderat") || senderLower.contains("صادرات")) -> "بانک صادرات"
            !isSenderNumeric && (senderLower.contains("sepah") || senderLower.contains("سپه")) -> "بانک سپه"
            !isSenderNumeric && (senderLower.contains("tejarat") || senderLower.contains("تجارت")) -> "بانک تجارت"
            !isSenderNumeric && (senderLower.contains("pasargad") || senderLower.contains("پاسارگاد")) -> "بانک پاسارگاد"
            !isSenderNumeric && (senderLower.contains("saman") || senderLower.contains("سامان")) -> "بانک سامان"
            !isSenderNumeric && (senderLower.contains("maskan") || senderLower.contains("مسکن")) -> "بانک مسکن"
            !isSenderNumeric && (senderLower.contains("refah") || senderLower.contains("رفاه")) -> "بانک رفاه کارگران"
            !isSenderNumeric && (senderLower.contains("shahr") || senderLower.contains("شهر")) -> "بانک شهر"
            !isSenderNumeric && (senderLower.contains("ayandeh") || senderLower.contains("آینده")) -> "بانک آینده"
            !isSenderNumeric && (senderLower.contains("parsian") || senderLower.contains("پارسیان")) -> "بانک پارسیان"
            !isSenderNumeric && (senderLower.contains("blu") || senderLower.contains("بلو")) -> "بلو بانک"

            // ۳. اگر فرستنده متن غیر عددی بود
            !isSenderNumeric && normalizedSender.isNotBlank() && normalizedSender != "ناشناس" -> normalizedSender

            else -> "سایر"
        }

        // ۱. بررسی کلمات کلیدی تراکنش
        val isExpense = text.contains("برداشت") ||
                text.contains("خرید") ||
                text.contains("انتقال") ||
                text.contains("حواله") ||
                text.contains("خروج") ||
                text.contains("پایا") ||
                text.contains("ساتنا") ||
                text.contains("پرداخت") ||
                text.contains("کارمزد") ||
                text.contains("-")

        val isIncome = text.contains("واریز") ||
                text.contains("سپاس") ||
                text.contains("بستانکار") ||
                text.contains("دریافت") ||
                text.contains("سود") ||
                text.contains("برگشت") ||
                text.contains("+")

        if (!isExpense && !isIncome) return null

        // ۲. استخراج مبلغ تراکنش
        val lines = normalizedBody.lines()
        var extractedAmount: Double? = null

        val amountRegex = Regex("([+-]?\\s*[0-9۰-۹,٬]+)\\s*(ریال|تومان)?")

        // ترجیح ۱: بررسی خطوط مربوط به خود تراکنش (به جز خط مانده حساب)
        for (line in lines) {
            val lineLower = line.lowercase()
            if (lineLower.contains("مانده") && !lineLower.contains("مبلغ") && !lineLower.contains("+") && !lineLower.contains("-")) {
                continue
            }

            val match = amountRegex.find(line)
            if (match != null) {
                val valStr = cleanNumber(match.groupValues[1])
                val valDouble = valStr.toDoubleOrNull() ?: 0.0
                if (valDouble > 0) {
                    extractedAmount = valDouble
                    break
                }
            }
        }

        // ترجیح ۲: اگر در خطوط بالا پیدا نشد، بررسی کل متن
        if (extractedAmount == null || extractedAmount <= 0.0) {
            val matches = amountRegex.findAll(normalizedBody)
            for (match in matches) {
                val valStr = cleanNumber(match.groupValues[1])
                val valDouble = valStr.toDoubleOrNull() ?: 0.0
                if (valDouble > 0) {
                    extractedAmount = valDouble
                    break
                }
            }
        }

        val amount = extractedAmount ?: return null
        if (amount <= 0.0) return null

        // ۳. تعیین دقیق نوع و عنوان تراکنش
        val finalIsIncome = if (text.contains("+") && !text.contains("برداشت")) {
            true
        } else if (isIncome && !isExpense) {
            true
        } else {
            false
        }

        val type = if (finalIsIncome) "INCOME" else "EXPENSE"

        val title = when {
            text.contains("پایا") -> "انتقال پایا"
            text.contains("ساتنا") -> "انتقال ساتنا"
            text.contains("پل") -> "انتقال پل"
            text.contains("کارت به کارت") -> "کارت به کارت"
            text.contains("خرید") || text.contains("pos") || text.contains("پوز") -> "خرید فروشگاهی"
            text.contains("اینترنتی") || text.contains("درگاه") -> "خرید اینترنتی"
            text.contains("خودپرداز") || text.contains("atm") -> "تراکنش خودپرداز"
            text.contains("سود") -> "سود بانکی"
            finalIsIncome -> "واریز به حساب"
            else -> "برداشت وجه"
        }

        val category = if (type == "INCOME") {
            when {
                text.contains("حقوق") || text.contains("دستمزد") -> "حقوق و درآمد"
                text.contains("سود") || text.contains("سرمایه") -> "سود و سرمایه‌گذاری"
                else -> "درآمد و واریزی"
            }
        } else {
            when {
                text.contains("خوراک") || text.contains("سوپر") || text.contains("هایپر") || text.contains("رستوران") || text.contains("فست فود") || text.contains("نانوا") || text.contains("کافه") -> "خوراک و سوپرمارکت"
                text.contains("قبض") || text.contains("شارژ") || text.contains("آب") || text.contains("برق") || text.contains("گاز") || text.contains("تلفن") || text.contains("همراه") -> "قبوض و خدمات"
                text.contains("اسنپ") || text.contains("تپسی") || text.contains("تاکسی") || text.contains("بنزین") || text.contains("پمپ") || text.contains("خودرو") || text.contains("پارکینگ") -> "حمل‌ونقل و خودرو"
                text.contains("دارو") || text.contains("پزشک") || text.contains("بیمارستان") || text.contains("درمان") || text.contains("مطب") || text.contains("داروخانه") -> "سلامت و درمان"
                text.contains("خرید") || text.contains("پوز") || text.contains("pos") || text.contains("فروشگاه") || text.contains("دیجی کالا") || text.contains("دیجیکالا") -> "خرید و بازار"
                text.contains("پایا") || text.contains("ساتنا") || text.contains("پل") || text.contains("کارت به کارت") || text.contains("انتقال") || text.contains("حواله") -> "انتقال مالی"
                else -> "سایر"
            }
        }

        return ParsedTransaction(
            title = title,
            amount = amount,
            type = type,
            category = category,
            bankName = bankName,
            rawMessage = messageBody
        )
    }

    private fun cleanNumber(str: String): String {
        return str
            .replace("+", "")
            .replace("-", "")
            .replace(",", "")
            .replace("٬", "")
            .replace(" ", "")
            .replace("۰", "0").replace("۱", "1").replace("۲", "2")
            .replace("۳", "3").replace("۴", "4").replace("۵", "5")
            .replace("۶", "6").replace("۷", "7").replace("۸", "8").replace("۹", "9")
            .replace("٠", "0").replace("١", "1").replace("٢", "2")
            .replace("٣", "3").replace("٤", "4").replace("٥", "5")
            .replace("٦", "6").replace("٧", "7").replace("٨", "8").replace("٩", "9")
    }
}
