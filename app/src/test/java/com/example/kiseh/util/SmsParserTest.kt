package com.example.kiseh.util

import org.junit.Assert.*
import org.junit.Test

class SmsParserTest {

    @Test
    fun testNegativeAmountTransactionParsing() {
        val sms = """
            777.888.15325926.1
            -300,000
            08/24_08:30

            مانده: 30,072,240
        """.trimIndent()

        val result = SmsParser.parseSms(sms, "Bank")
        assertNotNull("SMS with negative amount and balance should be parsed as a transaction", result)
        assertEquals(300000.0, result!!.amount, 0.001)
        assertEquals("EXPENSE", result.type)
    }

    @Test
    fun testBlueBankOtpSmsIgnored() {
        val sms = """
            بلو
            بفرمایید رمز پویا
            خرید
            نو آوران فن آوا
            مبلغ: 113,884,000 ریال
            رمز: 484155 
            22:02
        """.trimIndent()

        val result = SmsParser.parseSms(sms, "Blu")
        assertNull("OTP SMS containing 'خرید' and 'مبلغ' should be ignored and return null", result)
    }

    @Test
    fun testPasargadOtpSmsIgnored() {
        val sms = """
            پاسارگاد 
            خرید 
            خدمات آموزشی كاركنان 
            مبلغ:5,000,000 
            رمز: 16039 
            22:32:30
        """.trimIndent()

        val result = SmsParser.parseSms(sms, "Pasargad")
        assertNull("Pasargad OTP SMS containing 'رمز:' should be ignored and return null", result)
    }

    @Test
    fun testNormalWithdrawalParsing() {
        val sms = "برداشت مبلغ 1,500,000 ریال از حساب شما. مانده: 10,000,000"
        val result = SmsParser.parseSms(sms, "BankMelli")
        assertNotNull(result)
        assertEquals(1500000.0, result!!.amount, 0.001)
        assertEquals("EXPENSE", result.type)
    }

    @Test
    fun testNormalDepositParsing() {
        val sms = "واریز مبلغ 5,000,000 ریال به حساب شما. مانده: 15,000,000"
        val result = SmsParser.parseSms(sms, "BankMellat")
        assertNotNull(result)
        assertEquals(5000000.0, result!!.amount, 0.001)
        assertEquals("INCOME", result.type)
    }

    @Test
    fun testSnappEmergencyContactSmsIgnored() {
        val sms = """
            امنیت سفر اسنپ
            مسافر با شماره موبایل +989112335743 شما را به‌عنوان «مخاطب اضطراری» انتخاب کرده است.
            1- مشخصات سفر این مسافر را به‌صورت خودکار برای‌تان ارسال می‌کنیم.
            2- اگر در شرایط اضطراری نتوانیم با مسافر ارتباط برقرار کنیم با شما تماس می‌گیریم.

            اگر این پیامک به اشتباه برای‌تان ارسال شده می‌توانید با پشتیبانی اسنپ تماس بگیرید.
        """.trimIndent()

        val result = SmsParser.parseSms(sms, "Snapp")
        assertNull("Snapp emergency contact SMS should be ignored and not parsed as deposit", result)
    }

    @Test
    fun testSmsWithPhoneNumberAndAmountParsing() {
        val sms = "واریز مبلغ 1,000,000 ریال به حساب شما بابت شماره 09123456789. مانده: 2,000,000"
        val result = SmsParser.parseSms(sms, "BankMelli")
        assertNotNull(result)
        assertEquals(1000000.0, result!!.amount, 0.001)
        assertEquals("INCOME", result.type)
    }

    @Test
    fun testCompactDepositWithDateAndBalance() {
        val sms = """
            حساب9913379009
            واریز478,000,000
            مانده481,407,987
            05/08/27-10:35
        """.trimIndent()

        val result = SmsParser.parseSms(sms, "Bank")
        assertNotNull(result)
        assertEquals(478000000.0, result!!.amount, 0.001)
        assertEquals("INCOME", result.type)
    }

    @Test
    fun testBluBankWithdrawalParsing() {
        val sms = """
            بلو
            انتقال پل
            سجاد عزیز، 2,224,260 ریال از حساب شما پرید.
            موجودی: 1,671,636 ریال
            ۲۳:۰۱
            ۱۴۰۵.۰۸.۲۷
        """.trimIndent()

        val result = SmsParser.parseSms(sms, "Blu")
        assertNotNull(result)
        assertEquals(2224260.0, result!!.amount, 0.001)
        assertEquals("EXPENSE", result.type)
        assertEquals("انتقال پل", result.title)
    }

    @Test
    fun testBluBankDepositRefundParsing() {
        val sms = """
            بلو
            پوزش بابت اختلال
            سجاد عزیز، 2,224,260 ریال به حساب شما نشست.
            موجودی: 3,903,896 ریال
            ۲۳:۰۱
            ۱۴۰۵.۰۸.۲۷
        """.trimIndent()

        val result = SmsParser.parseSms(sms, "Blu")
        assertNotNull(result)
        assertEquals(2224260.0, result!!.amount, 0.001)
        assertEquals("INCOME", result.type)
        assertEquals("برگشت وجه", result.title)
    }

    @Test
    fun testBrokerageMofidProfitParsing() {
        val sms = """
            کارگزاری مفید
            جناب آقای سجاد رجا،

            مبلغ ۱۴,۲۹۹,۶۷۱ ریال بابت سود طرح حامی در آبان ماه(معادل ۲.۶۸ درصد ماهانه و سود موثر سالانه ۳۸ درصد)، به قدرت خرید شما اضافه شد.
        """.trimIndent()

        val result = SmsParser.parseSms(sms, "مفید")
        assertNotNull(result)
        assertEquals(14299671.0, result!!.amount, 0.001)
        assertEquals("INCOME", result.type)
        assertEquals("سود سرمایه‌گذاری", result.title)
        assertEquals("سود و سرمایه‌گذاری", result.category)
        assertEquals("کارگزاری مفید", result.bankName)
    }
}
