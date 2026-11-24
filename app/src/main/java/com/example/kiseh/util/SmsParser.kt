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

        // فیلتر جامع پیامک‌های غیرتراکنشی، تبلیغاتی، امنیت سفر، پشتیبانی، کدهای ورود و وام
        if (text.contains("بسته مخصوص") || text.contains("بسته ویژه") ||
            text.contains("پیشنهاد ویژه") || text.contains("آخر ماه پرداخت") ||
            text.contains("پولش رو بعدا") || text.contains("پولش رو بعداً") ||
            text.contains("فعال کن") || text.contains("خرید بسته") ||
            text.contains("هدیه") || text.contains("قرعه کشی") ||
            text.contains("برنده") || text.contains("وام") ||
            text.contains("ایرانسل من") || text.contains("همراه من") ||
            text.contains("امنیت سفر") || text.contains("مخاطب اضطراری") ||
            text.contains("پشتیبانی اسنپ") || text.contains("پشتیبانی تپسی") ||
            text.contains("http") || text.contains("www.") ||
            text.contains("کلیک کنید")) {
            return null
        }

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
            text.contains("مفید") || text.contains("کارگزاری مفید") -> "کارگزاری مفید"
            text.contains("آگاه") || text.contains("کارگزاری آگاه") -> "کارگزاری آگاه"
            text.contains("کارگزاری") -> "کارگزاری"

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

        // فیلتر پیامک‌های رمز پویا، رمز یکبار مصرف، رمز عبور و کدهای تایید (پیش از بررسی تراکنش)
        val isOtpMessage = text.contains("رمز پویا") ||
                text.contains("رمز یکبار مصرف") ||
                text.contains("کد تایید") ||
                text.contains("کد فعالسازی") ||
                text.contains("رمز دوم") ||
                text.contains("مرز پویا") ||
                text.contains("رمز یکبارمصرف") ||
                text.contains("کد تاییدیه") ||
                text.contains("کد اعتبارسنجی") ||
                text.contains("رمز:") ||
                text.contains("رمز :") ||
                (text.contains("رمز") && text.contains("مبلغ") && !text.contains("مانده") && !text.contains("موجودی") && !text.contains("پیگیری") && !text.contains("مرجع"))

        if (isOtpMessage) return null

        // بررسی مبلغ منفی همراه با مانده/موجودی (جهت شناسایی تراکنش‌های بدون کلمات کلیدی متنی مثل برداشت/خرید)
        val hasNegativeAmount = normalizedBody.lines().any { line ->
            val trimmed = line.trim()
            val isDateTime = trimmed.contains(Regex("[0-9]{2,4}[/-][0-9]{1,2}[/-][0-9]{1,2}")) ||
                    trimmed.contains(Regex("[0-9]{1,2}:[0-9]{2}"))
            if (isDateTime) {
                false
            } else {
                trimmed.startsWith("-") || trimmed.contains(Regex("(?:^|\\s|:)-\\s*[0-9۰-۹,٬]+"))
            }
        }

        // پاکسازی شماره تلفن‌های ایران و عبارات مالی ویژه (مانند قدرت خرید) جهت جلوگیری از اشتباه در تشخیص کلمات کلیدی
        val phoneRegex = Regex("(?:\\+?98|0)?9\\d{9}|\\+98\\d{8,11}")
        val textWithoutPhones = text.replace(phoneRegex, "")
        val textForKeywordCheck = textWithoutPhones
            .replace("قدرت خرید", "")
            .replace("اعتبار خرید", "")

        // ۱. بررسی کلمات کلیدی تراکنش
        val isExpense = textForKeywordCheck.contains("برداشت") ||
                textForKeywordCheck.contains("خرید") ||
                textForKeywordCheck.contains("انتقال") ||
                textForKeywordCheck.contains("حواله") ||
                textForKeywordCheck.contains("خروج") ||
                textForKeywordCheck.contains("پایا") ||
                textForKeywordCheck.contains("ساتنا") ||
                textForKeywordCheck.contains("پرداخت") ||
                textForKeywordCheck.contains("کارمزد") ||
                textForKeywordCheck.contains("بدهکار") ||
                textForKeywordCheck.contains("پوز") ||
                textForKeywordCheck.contains("پرید") ||
                textForKeywordCheck.contains("خرج شد") ||
                (hasNegativeAmount && (textWithoutPhones.contains("مانده") || textWithoutPhones.contains("موجودی")))

        val isIncome = textWithoutPhones.contains("واریز") ||
                textWithoutPhones.contains("سپاس") ||
                textWithoutPhones.contains("بستانکار") ||
                textWithoutPhones.contains("دریافت") ||
                textWithoutPhones.contains("سود") ||
                textWithoutPhones.contains("برگشت") ||
                textWithoutPhones.contains("نشست") ||
                textWithoutPhones.contains("شارژ شد") ||
                textWithoutPhones.contains("اضافه شد") ||
                textWithoutPhones.contains("+")

        if (!isExpense && !isIncome) return null

        // ۲. استخراج مبلغ تراکنش
        val lines = normalizedBody.lines()
        var extractedAmount: Double? = null

        val amountRegex = Regex("([+-]?\\s*[0-9۰-۹,٬]+)\\s*(ریال|تومان)?")

        // ترجیح ۱: بررسی خطوط مربوط به خود تراکنش (اطمینان از داشتن کاما، واحد پول، علامت یا کلمه مبلغ برای جلوگیری از خواندن شماره حساب)
        for (line in lines) {
            val lineLower = line.lowercase()
            if (line.count { it == '.' } >= 2) {
                continue
            }
            if (lineLower.contains("مانده") && !lineLower.contains("مبلغ") && !lineLower.contains("+") && !lineLower.contains("-")) {
                continue
            }

            val matches = amountRegex.findAll(line)
            for (match in matches) {
                val rawNumStr = match.groupValues[1]
                val unitStr = match.groupValues[2]
                val valStr = cleanNumber(rawNumStr)

                // رد کردن شماره تلفن، شماره کارت یا کدهای عددی طولانی بدون کاما
                if (isInvalidAmountCandidate(rawNumStr, valStr)) {
                    continue
                }

                val valDouble = valStr.toDoubleOrNull() ?: 0.0

                val hasComma = rawNumStr.contains(",") || rawNumStr.contains("٬")
                val hasUnit = unitStr.isNotBlank()
                val hasSign = rawNumStr.trim().startsWith("+") || rawNumStr.trim().startsWith("-")
                val hasMablagh = lineLower.contains("مبلغ")

                if (valDouble >= 1000.0 && (hasComma || hasUnit || hasSign || hasMablagh)) {
                    extractedAmount = valDouble
                    break
                }
            }
            if (extractedAmount != null) break
        }

        // ترجیح ۲: اگر در خطوط بالا پیدا نشد، بررسی کل متن
        if (extractedAmount == null) {
            val matches = amountRegex.findAll(normalizedBody)
            for (match in matches) {
                val rawNumStr = match.groupValues[1]
                val unitStr = match.groupValues[2]
                val valStr = cleanNumber(rawNumStr)

                if (isInvalidAmountCandidate(rawNumStr, valStr)) {
                    continue
                }

                val valDouble = valStr.toDoubleOrNull() ?: 0.0

                val hasComma = rawNumStr.contains(",") || rawNumStr.contains("٬")
                val hasUnit = unitStr.isNotBlank()
                val hasSign = rawNumStr.trim().startsWith("+") || rawNumStr.trim().startsWith("-")

                if (valDouble >= 1000.0 && (hasComma || hasUnit || hasSign)) {
                    extractedAmount = valDouble
                    break
                }
            }
        }

        val amount = extractedAmount ?: return null

        // ۳. تعیین دقیق نوع و عنوان تراکنش
        val finalIsIncome = when {
            textWithoutPhones.contains("واریز") || textWithoutPhones.contains("بستانکار") || textWithoutPhones.contains("سود") || textWithoutPhones.contains("نشست") || textWithoutPhones.contains("شارژ شد") || textWithoutPhones.contains("اضافه شد") -> {
                !textForKeywordCheck.contains("برداشت") && !textForKeywordCheck.contains("خرید") && !textForKeywordCheck.contains("پرید")
            }
            textWithoutPhones.contains("+") && !textForKeywordCheck.contains("برداشت") && !textForKeywordCheck.contains("پرید") -> true
            else -> isIncome && !isExpense
        }

        val type = if (finalIsIncome) "INCOME" else "EXPENSE"

        val title = when {
            text.contains("پایا") -> "انتقال پایا"
            text.contains("ساتنا") -> "انتقال ساتنا"
            text.contains("پل") -> "انتقال پل"
            text.contains("کارت به کارت") -> "کارت به کارت"
            text.contains("برگشت") || text.contains("پوزش") -> "برگشت وجه"
            text.contains("سود") -> if (text.contains("کارگزاری") || text.contains("حامی") || text.contains("سرمایه")) "سود سرمایه‌گذاری" else "سود بانکی"
            textForKeywordCheck.contains("خرید") || text.contains("pos") || (text.contains("پوز") && !text.contains("پوزش")) -> "خرید فروشگاهی"
            text.contains("اینترنتی") || text.contains("درگاه") -> "خرید اینترنتی"
            text.contains("خودپرداز") || text.contains("atm") -> "تراکنش خودپرداز"
            finalIsIncome -> "واریز به حساب"
            else -> "برداشت وجه"
        }

        val category = if (type == "INCOME") {
            when {
                text.contains("حقوق") || text.contains("دستمزد") -> "حقوق و درآمد"
                text.contains("سود") || text.contains("سرمایه") || text.contains("کارگزاری") || text.contains("حامی") -> "سود و سرمایه‌گذاری"
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

    private fun isInvalidAmountCandidate(rawNumStr: String, cleanStr: String): Boolean {
        val rawTrimmed = rawNumStr.trim()
        // ۱. بررسی شماره تلفن (مانند +989112335743 یا 09112335743 یا 989112335743)
        val isPhone = rawTrimmed.startsWith("+98") ||
                cleanStr.matches(Regex("^(98|0)?9\\d{9}$")) ||
                (cleanStr.startsWith("09") && cleanStr.length == 11)
        if (isPhone) return true

        // ۲. رشته‌های عددی طولانی بدون کاما، واحد پول یا کلمه "مبلغ" (مانند شماره کارت، حساب یا پیگیری)
        val hasComma = rawNumStr.contains(",") || rawNumStr.contains("٬")
        val hasSign = rawTrimmed.startsWith("+") || rawTrimmed.startsWith("-")
        return !hasComma && cleanStr.length >= 10 && !hasSign
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
