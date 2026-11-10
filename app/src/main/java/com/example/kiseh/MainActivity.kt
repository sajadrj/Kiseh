package com.example.kiseh

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.kiseh.data.local.entity.AppDatabase
import com.example.kiseh.data.local.entity.TransactionEntity
import com.example.kiseh.util.BudgetManager
import com.example.kiseh.util.SmsContentObserver
import com.example.kiseh.util.SmsReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

// پالت رنگی مدرن، لوکس و شیک مناسب اپلیکیشن مالی (کیسه)
private val KisehColorScheme = lightColorScheme(
    primary = Color(0xFF0D9488),         // Teal مدرن و جذاب
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF99F6E4),
    onPrimaryContainer = Color(0xFF042F2E),
    secondary = Color(0xFF6366F1),       // Indigo مدرن
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0E7FF),
    onSecondaryContainer = Color(0xFF312E81),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569)
)

class MainActivity : ComponentActivity() {

    private val dynamicSmsReceiver = SmsReceiver()
    private lateinit var smsContentObserver: SmsContentObserver

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.READ_SMS] == true || permissions[Manifest.permission.RECEIVE_SMS] == true
        if (granted) {
            Toast.makeText(this, "مجوز دسترسی به پیامک فعال شد", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        smsContentObserver = SmsContentObserver(this)

        // بررسی و درخواست مجوزهای خواندن، دریافت پیامک و نوتیفیکیشن
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_SMS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECEIVE_SMS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }

        val database = AppDatabase.getDatabase(this)
        val transactionDao = database.transactionDao()

        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme(colorScheme = KisehColorScheme) {
                    val transactions by transactionDao.getAllTransactions().collectAsState(initial = emptyList())
                    var showDialog by remember { mutableStateOf(false) }
                    val coroutineScope = rememberCoroutineScope()

                    Scaffold(
                        modifier = Modifier
                            .fillMaxSize()
                            .systemBarsPadding(),
                        floatingActionButton = {
                            FloatingActionButton(
                                onClick = { showDialog = true },
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "افزودن تراکنش")
                            }
                        }
                    ) { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding)) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                MainScreen(
                                    transactions = transactions,
                                    onDeleteTransaction = { idToDelete ->
                                        coroutineScope.launch(Dispatchers.IO) {
                                            transactionDao.deleteTransactionById(idToDelete)
                                        }
                                    }
                                )
                            }

                            if (showDialog) {
                                AddTransactionDialog(
                                    onDismiss = { showDialog = false },
                                    onConfirm = { title, amount, type, category, bankName ->
                                        coroutineScope.launch(Dispatchers.IO) {
                                            transactionDao.insertTransaction(
                                                TransactionEntity(
                                                    title = title,
                                                    amount = amount,
                                                    type = type,
                                                    category = category,
                                                    bankName = bankName,
                                                    date = System.currentTimeMillis()
                                                )
                                            )
                                        }
                                        showDialog = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        try {
            contentResolver.registerContentObserver(
                Uri.parse("content://sms"),
                true,
                smsContentObserver
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val filter = IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION).apply {
            priority = 999
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(dynamicSmsReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(dynamicSmsReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            contentResolver.unregisterContentObserver(smsContentObserver)
            unregisterReceiver(dynamicSmsReceiver)
        } catch (e: Exception) {
            // نادیده گرفتن در صورت عدم ثبت
        }
    }
}

fun formatRial(amount: Double): String {
    return try {
        val formatter = NumberFormat.getInstance(Locale.forLanguageTag("fa"))
        formatter.format(amount) + " ریال"
    } catch (e: Exception) {
        "${amount.toLong()} ریال"
    }
}

fun gregorianToJalali(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
    val g_days_in_month = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    val gyAdj = gy - 1600
    val gmAdj = gm - 1
    val gdAdj = gd - 1

    var g_day_no = 365 * gyAdj + (gyAdj + 3) / 4 - (gyAdj + 99) / 100 + (gyAdj + 399) / 400
    for (i in 0 until gmAdj) {
        g_day_no += g_days_in_month[i]
    }
    if (gmAdj > 1 && ((gyAdj % 4 == 0 && gyAdj % 100 != 0) || (gyAdj % 400 == 0))) {
        g_day_no++
    }
    g_day_no += gdAdj

    var j_day_no = g_day_no - 79
    val j_np = j_day_no / 12053
    j_day_no %= 12053

    var jy = 979 + 33 * j_np + 4 * (j_day_no / 1461)
    j_day_no %= 1461

    if (j_day_no >= 365) {
        jy += (j_day_no - 1) / 365
        j_day_no = (j_day_no - 1) % 365
    }

    var jm = 1
    var jd = 1
    for (i in 0..11) {
        val days = when {
            i < 6 -> 31
            i < 11 -> 30
            else -> 29
        }
        if (j_day_no < days) {
            jm = i + 1
            jd = j_day_no + 1
            break
        }
        j_day_no -= days
    }
    return Triple(jy, jm, jd)
}

fun formatDate(timestamp: Long, pattern: String): String {
    return try {
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        val gYear = calendar.get(Calendar.YEAR)
        val gMonth = calendar.get(Calendar.MONTH) + 1
        val gDay = calendar.get(Calendar.DAY_OF_MONTH)

        val (jY, jM, jD) = gregorianToJalali(gYear, gMonth, gDay)
        val jMonthStr = if (jM < 10) "0$jM" else "$jM"
        val jDayStr = if (jD < 10) "0$jD" else "$jD"

        when (pattern) {
            "yyyy/MM/dd" -> "$jY/$jMonthStr/$jDayStr"
            "yyyy/MM" -> "$jY/$jMonthStr"
            "yyyy" -> "$jY"
            else -> "$jY/$jMonthStr/$jDayStr"
        }
    } catch (e: Exception) {
        ""
    }
}

fun getCategoryColor(categoryName: String): Color {
    return when (categoryName) {
        "خوراک و سوپرمارکت" -> Color(0xFFEF4444)
        "خرید و بازار" -> Color(0xFFF97316)
        "قبوض و خدمات" -> Color(0xFFEAB308)
        "حمل‌ونقل و خودرو" -> Color(0xFF3B82F6)
        "سلامت و درمان" -> Color(0xFF8B5CF6)
        "انتقال مالی" -> Color(0xFFEC4899)
        "حقوق و درآمد" -> Color(0xFF10B981)
        "سود و سرمایه‌گذاری" -> Color(0xFF06B6D4)
        "درآمد و واریزی" -> Color(0xFF14B8A6)
        "دستی" -> Color(0xFF6366F1)
        "پیامک خودکار" -> Color(0xFF0D9488)
        else -> {
            val palette = listOf(
                Color(0xFFEF4444), Color(0xFFF97316), Color(0xFFEAB308),
                Color(0xFF10B981), Color(0xFF06B6D4), Color(0xFF3B82F6),
                Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFF64748B)
            )
            val hash = abs(categoryName.hashCode())
            palette[hash % palette.size]
        }
    }
}

@Composable
fun CategoryPieChart(
    categoryTotals: List<Pair<String, Double>>,
    totalAmount: Double,
    modifier: Modifier = Modifier
) {
    if (totalAmount <= 0.0 || categoryTotals.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(140.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "داده‌ای برای این دسته وجود ندارد.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.size(170.dp)
        ) {
            var startAngle = -90f
            val strokeWidth = 32.dp.toPx()
            val canvasSize = size.minDimension - strokeWidth

            categoryTotals.forEach { (category, amount) ->
                val sweepAngle = ((amount / totalAmount) * 360f).toFloat()
                val color = getCategoryColor(category)

                drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                    size = Size(canvasSize, canvasSize),
                    topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                )

                startAngle += sweepAngle
            }
        }

        // متن مرکز نمودار دونات
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "مجموع",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatRial(totalAmount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
fun MainScreen(
    transactions: List<TransactionEntity>,
    onDeleteTransaction: (Long) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Transactions, 1: Statistics
    var showAboutDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // هدر شیک و مدرن برنامه با آیکون آدمک در سمت چپ
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "کیف پول هوشمند کیسه",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "مدیریت خودکار تراکنش‌های بانکی",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // آیکون آدمک (پروفایل / درباره توسعه‌دهنده)
            IconButton(
                onClick = { showAboutDialog = true },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "درباره توسعه‌دهنده",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // تب‌های مدرن با عرض برابر در کل صفحه (TabRow)
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("تراکنش‌ها") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("آمار و گزارش‌ها") }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (selectedTab) {
            0 -> TransactionsTab(transactions, onDeleteTransaction)
            1 -> StatisticsTab(transactions)
        }

        // دیالوگ صفحه درباره توسعه‌دهنده
        if (showAboutDialog) {
            AlertDialog(
                onDismissRequest = { showAboutDialog = false },
                title = { Text("درباره توسعه‌دهنده") },
                text = {
                    AboutContent()
                },
                confirmButton = {
                    TextButton(onClick = { showAboutDialog = false }) {
                        Text("بستن")
                    }
                },
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
}

@Composable
fun TransactionsTab(
    transactions: List<TransactionEntity>,
    onDeleteTransaction: (Long) -> Unit
) {
    var selectedBank by remember { mutableStateOf("همه بانک‌ها") }
    var selectedCategory by remember { mutableStateOf("همه دسته‌ها") }
    var searchQuery by remember { mutableStateOf("") }

    val totalIncome = transactions.filter { it.type == "INCOME" }.sumOf { it.amount }
    val totalExpense = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
    val netBalance = totalIncome - totalExpense

    val availableBanks = listOf("همه بانک‌ها") + transactions.map { it.bankName }.distinct()
    val availableCategories = listOf("همه دسته‌ها") + transactions.map { it.category }.distinct()

    val filteredTransactions = transactions.filter { tx ->
        val matchesBank = selectedBank == "همه بانک‌ها" || tx.bankName == selectedBank
        val matchesCategory = selectedCategory == "همه دسته‌ها" || tx.category == selectedCategory
        val matchesSearch = searchQuery.isBlank() ||
                tx.title.contains(searchQuery, ignoreCase = true) ||
                tx.amount.toString().contains(searchQuery) ||
                tx.bankName.contains(searchQuery, ignoreCase = true) ||
                tx.category.contains(searchQuery, ignoreCase = true)
        matchesBank && matchesCategory && matchesSearch
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // نوار جستجو
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("جستجو بر اساس نام پذیرنده، دسته یا مبلغ...") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(16.dp),
            singleLine = true
        )

        // کارت خلاصه آمار و مجموع مبالغ با طراحی مدرن
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "خلاصه وضعیت مالی",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = "کل واریزی‌ها:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(text = formatRial(totalIncome), color = Color(0xFF047857), style = MaterialTheme.typography.bodyMedium)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "کل برداشت‌ها:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(text = formatRial(totalExpense), color = Color(0xFFDC2626), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "موجودی خالص کل:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        text = formatRial(netBalance),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (netBalance >= 0) Color(0xFF047857) else Color(0xFFDC2626)
                    )
                }
            }
        }

        // نوار انتخاب دسته‌بندی و بانک (Filter Chips)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // فیلتر دسته‌بندی‌ها
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableCategories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat) },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // فیلتر بانک‌ها
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableBanks.forEach { bank ->
                    FilterChip(
                        selected = selectedBank == bank,
                        onClick = { selectedBank = bank },
                        label = { Text(bank) },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        // لیست تراکنش‌ها
        if (filteredTransactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "هیچ تراکنشی در این دسته وجود ندارد.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredTransactions, key = { it.id }) { tx ->
                    val isIncome = tx.type == "INCOME"
                    val amountColor = if (isIncome) {
                        Color(0xFF047857) // سبز تیره
                    } else {
                        Color(0xFFDC2626) // قرمز تیره
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = tx.title, style = MaterialTheme.typography.bodyLarge)
                                    Surface(
                                        color = getCategoryColor(tx.category).copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = tx.category,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = getCategoryColor(tx.category)
                                        )
                                    }
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = tx.bankName,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "مبلغ: ${formatRial(tx.amount)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = amountColor
                                )
                                Text(
                                    text = if (isIncome) "واریز" else "برداشت",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = amountColor
                                )
                            }

                            // دکمه حذف (آیکون سطل زباله)
                            IconButton(
                                onClick = { onDeleteTransaction(tx.id) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "حذف تراکنش",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatisticsTab(transactions: List<TransactionEntity>) {
    var timeFrame by remember { mutableStateOf("ماه") } // "روز", "ماه", "سال"
    var categoryTypeFilter by remember { mutableStateOf("EXPENSE") } // "EXPENSE" یا "INCOME"

    val pattern = when (timeFrame) {
        "روز" -> "yyyy/MM/dd"
        "ماه" -> "yyyy/MM"
        "سال" -> "yyyy"
        else -> "yyyy/MM"
    }

    val totalIncome = transactions.filter { it.type == "INCOME" }.sumOf { it.amount }
    val totalExpense = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }

    // تفکیک بر اساس دسته‌بندی با کوئری / گروه‌بندی
    val categoryTotals = transactions
        .filter { it.type == categoryTypeFilter }
        .groupBy { it.category }
        .mapValues { entry -> entry.value.sumOf { it.amount } }
        .toList()
        .sortedByDescending { it.second }

    val categoryTotalSum = categoryTotals.sumOf { it.second }

    // تفکیک بر اساس بانک
    val bankTotals = transactions.groupBy { it.bankName }.mapValues { entry ->
        val inc = entry.value.filter { it.type == "INCOME" }.sumOf { it.amount }
        val exp = entry.value.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        inc - exp
    }

    // گروه‌بندی بر اساس بازه زمانی (روز، ماه، سال)
    val groupedByPeriod = transactions.groupBy { formatDate(it.date, pattern) }.toSortedMap(reverseOrder())

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // انتخاب بازه زمانی (روز، ماه، سال)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("روز", "ماه", "سال").forEach { tf ->
                    FilterChip(
                        selected = timeFrame == tf,
                        onClick = { timeFrame = tf },
                        label = { Text("بر اساس $tf") },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        item {
            // کارت خلاصه کل
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(text = "آمار کلی ($timeFrame)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "کل واریزی‌ها:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(text = formatRial(totalIncome), color = Color(0xFF047857), style = MaterialTheme.typography.bodyMedium)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = "کل برداشت‌ها:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(text = formatRial(totalExpense), color = Color(0xFFDC2626), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        item {
            // کارت نمودار دایره‌ای دسته‌بندی هزینه‌ها و درآمدها
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📊 نمودار دسته‌بندی تراکنش‌ها",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(
                                selected = categoryTypeFilter == "EXPENSE",
                                onClick = { categoryTypeFilter = "EXPENSE" },
                                label = { Text("هزینه‌ها") },
                                shape = RoundedCornerShape(12.dp)
                            )
                            FilterChip(
                                selected = categoryTypeFilter == "INCOME",
                                onClick = { categoryTypeFilter = "INCOME" },
                                label = { Text("درآمدها") },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    HorizontalDivider()

                    // رسم نمودار دایره‌ای
                    CategoryPieChart(
                        categoryTotals = categoryTotals,
                        totalAmount = categoryTotalSum,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    // راهنما و لیست جزئیات هر دسته همراه با بودجه
                    if (categoryTotals.isNotEmpty() && categoryTotalSum > 0) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            categoryTotals.forEach { (catName, amount) ->
                                CategoryBudgetRow(
                                    categoryName = catName,
                                    timeFrame = timeFrame,
                                    spentAmount = amount,
                                    totalExpense = categoryTotalSum
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            // نمودار تفکیک بانک‌ها
            if (bankTotals.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(text = "📊 تفکیک موجودی بانک‌ها", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        HorizontalDivider()
                        bankTotals.forEach { (bank, balance) ->
                            val maxAbs = bankTotals.values.maxOfOrNull { abs(it) } ?: 1.0
                            val progress = (abs(balance) / if (maxAbs == 0.0) 1.0 else maxAbs).toFloat().coerceIn(0.1f, 1.0f)

                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = bank, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        text = formatRial(balance),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (balance >= 0) Color(0xFF047857) else Color(0xFFDC2626)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = if (balance >= 0) Color(0xFF10B981) else Color(0xFFF87171),
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "📅 گزارش تفکیک‌شده بر اساس $timeFrame",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        items(groupedByPeriod.entries.toList()) { (period, txList) ->
            val periodIncome = txList.filter { it.type == "INCOME" }.sumOf { it.amount }
            val periodExpense = txList.filter { it.type == "EXPENSE" }.sumOf { it.amount }
            val periodNet = periodIncome - periodExpense

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "بازه: $period", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = formatRial(periodNet),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (periodNet >= 0) Color(0xFF047857) else Color(0xFFDC2626)
                        )
                    }
                    Text(text = "تعداد تراکنش: ${txList.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "واریز: ${formatRial(periodIncome)}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF047857))
                        Text(text = "برداشت: ${formatRial(periodExpense)}", style = MaterialTheme.typography.bodySmall, color = Color(0xFFDC2626))
                    }
                }
            }
        }
    }
}

@Composable
fun AboutContent() {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // کارت پروفایل توسعه‌دهنده
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.size(70.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "توسعه‌دهنده",
                            modifier = Modifier.size(35.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                Text(
                    text = "سجاد رجبی",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Text(
                    text = "توسعه‌دهنده نرم‌افزار و اندروید",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }

        // کارت راه‌های ارتباطی
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "راه‌های ارتباطی",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                HorizontalDivider()

                // ایمیل
                ContactRow(
                    title = "ایمیل",
                    value = "sajad.rj95@gmail.com",
                    icon = Icons.Default.Email,
                    onClick = {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:sajad.rj95@gmail.com")
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "برنامه ایمیلی یافت نشد", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                // لینکدین
                ContactRow(
                    title = "لینکدین",
                    value = "linkedin.com/in/sajadrj",
                    icon = Icons.Default.Info,
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://linkedin.com/in/sajadrj"))
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "خطا در باز کردن لینک", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                // گیت‌هاب
                ContactRow(
                    title = "گیت‌هاب",
                    value = "github.com/sajadrj",
                    icon = Icons.Default.Info,
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/sajadrj"))
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "خطا در باز کردن لینک", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ContactRow(
    title: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(1.dp))
                Text(text = value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
fun AddTransactionDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Double, String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }
    var isIncome by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf("خوراک و سوپرمارکت") }
    var bankName by remember { mutableStateOf("بانک ملی") }

    val expenseCategories = listOf(
        "خوراک و سوپرمارکت",
        "خرید و بازار",
        "قبوض و خدمات",
        "حمل‌ونقل و خودرو",
        "سلامت و درمان",
        "انتقال مالی",
        "سایر"
    )

    val incomeCategories = listOf(
        "حقوق و درآمد",
        "سود و سرمایه‌گذاری",
        "درآمد و واریزی",
        "سایر"
    )

    val availableCategories = if (isIncome) incomeCategories else expenseCategories

    val banks = listOf(
        "بانک ملی",
        "بانک ملت",
        "بانک صادرات",
        "بانک سپه",
        "بانک تجارت",
        "بانک پاسارگاد",
        "بانک سامان",
        "بانک کشاورزی",
        "بانک مسکن",
        "بانک رفاه کارگران",
        "بانک شهر",
        "بانک دی",
        "بانک آینده",
        "بانک رسالت",
        "بانک مهر ایران",
        "بانک پارسیان",
        "بانک اقتصاد نوین",
        "بانک سینا",
        "بانک کارآفرین",
        "بانک گردشگری",
        "پست بانک ایران",
        "بلو بانک",
        "سایر"
    )
    var bankExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(isIncome) {
        category = if (isIncome) "حقوق و درآمد" else "خوراک و سوپرمارکت"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("افزودن تراکنش جدید") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان / پذیرنده") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("مبلغ (ریال)") },
                    modifier = Modifier.fillMaxWidth()
                )

                // انتخاب دسته‌بندی
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { categoryExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("دسته‌بندی: $category")
                    }
                    DropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false },
                        modifier = Modifier.heightIn(max = 250.dp)
                    ) {
                        availableCategories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    category = cat
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                // انتخاب بانک
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { bankExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("بانک: $bankName")
                    }
                    DropdownMenu(
                        expanded = bankExpanded,
                        onDismissRequest = { bankExpanded = false },
                        modifier = Modifier.heightIn(max = 250.dp)
                    ) {
                        banks.forEach { b ->
                            DropdownMenuItem(
                                text = { Text(b) },
                                onClick = {
                                    bankName = b
                                    bankExpanded = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("نوع تراکنش: ${if (isIncome) "واریز" else "برداشت"}")
                    Switch(
                        checked = isIncome,
                        onCheckedChange = { isIncome = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val formattedAmountStr = amountStr
                        .replace(",", "")
                        .replace("٬", "")
                        .replace("۰", "0").replace("۱", "1").replace("۲", "2")
                        .replace("۳", "3").replace("۴", "4").replace("۵", "5")
                        .replace("۶", "6").replace("۷", "7").replace("۸", "8").replace("۹", "9")
                        .replace("٠", "0").replace("١", "1").replace("٢", "2")
                        .replace("٣", "3").replace("٤", "4").replace("٥", "5")
                        .replace("٦", "6").replace("٧", "7").replace("٨", "8").replace("٩", "9")

                    val amount = formattedAmountStr.toDoubleOrNull() ?: 0.0
                    if (title.isNotBlank() && amount > 0) {
                        onConfirm(title, amount, if (isIncome) "INCOME" else "EXPENSE", category, bankName)
                    }
                }
            ) {
                Text("ثبت")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("لغو")
            }
        }
    )
}

@Composable
fun CategoryBudgetRow(
    categoryName: String,
    timeFrame: String,
    spentAmount: Double,
    totalExpense: Double
) {
    val context = LocalContext.current
    val budgetManager = remember { BudgetManager(context) }
    val budgetFlow = remember(categoryName, timeFrame) { budgetManager.getBudget(categoryName, timeFrame) }
    val budget by budgetFlow.collectAsState(initial = 0.0)

    var showBudgetDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val percentage = if (totalExpense > 0) (spentAmount / totalExpense) * 100 else 0.0
    val catColor = getCategoryColor(categoryName)

    val budgetProgress = if (budget > 0) (spentAmount / budget).toFloat().coerceIn(0f, 1.5f) else 0f
    val isExceeded = budget > 0 && spentAmount > budget
    val isNearLimit = budget > 0 && spentAmount >= budget * 0.85 && !isExceeded

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { showBudgetDialog = true }
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(catColor)
                )
                Text(text = categoryName, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "(${String.format(Locale.US, "%.1f", percentage)}%)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatRial(spentAmount),
                        style = MaterialTheme.typography.bodySmall,
                        color = catColor
                    )
                    if (budget > 0) {
                        Text(
                            text = "بودجه ($timeFrame): ${formatRial(budget)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isExceeded) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "بدون بودجه ($timeFrame)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                // آیکن تنظیمات/ویرایش بودجه که همیشه نمایش داده می‌شود
                IconButton(
                    onClick = { showBudgetDialog = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "تعیین/ویرایش بودجه",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        LinearProgressIndicator(
            progress = { if (budget > 0) budgetProgress.coerceIn(0f, 1f) else (percentage / 100f).toFloat().coerceIn(0.01f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = when {
                isExceeded -> Color(0xFFDC2626) // قرمز هشدار عبور از بودجه
                isNearLimit -> Color(0xFFF59E0B) // نارنجی نزدیک به سقف بودجه
                else -> catColor
            },
            trackColor = catColor.copy(alpha = 0.15f)
        )

        if (isExceeded) {
            Text(
                text = "⚠️ هشدار: سقف بودجه $timeFrame این دسته رد شده است!",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFDC2626)
            )
        } else if (isNearLimit) {
            Text(
                text = "⚠️ توجه: به سقف بودجه $timeFrame این دسته نزدیک می‌شوید.",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFF59E0B)
            )
        }
    }

    if (showBudgetDialog) {
        SetBudgetDialog(
            categoryName = categoryName,
            timeFrame = timeFrame,
            currentBudget = budget,
            onDismiss = { showBudgetDialog = false },
            onSave = { newBudget ->
                coroutineScope.launch(Dispatchers.IO) {
                    budgetManager.saveBudget(categoryName, timeFrame, newBudget)
                }
                showBudgetDialog = false
            }
        )
    }
}

@Composable
fun SetBudgetDialog(
    categoryName: String,
    timeFrame: String,
    currentBudget: Double,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var amountStr by remember { mutableStateOf(if (currentBudget > 0) currentBudget.toLong().toString() else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعیین سقف بودجه $timeFrame برای $categoryName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "لطفاً حداکثر مبلغ هزینه مجاز برای این دسته‌بندی در بازه زمانی ($timeFrame) را به ریال وارد کنید:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("مبلغ بودجه $timeFrame (ریال)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cleanStr = amountStr
                        .replace(",", "")
                        .replace("٬", "")
                        .replace("۰", "0").replace("۱", "1").replace("۲", "2")
                        .replace("۳", "3").replace("۴", "4").replace("۵", "5")
                        .replace("۶", "6").replace("۷", "7").replace("۸", "8").replace("۹", "9")
                    val amount = cleanStr.toDoubleOrNull() ?: 0.0
                    onSave(amount)
                }
            ) {
                Text("ذخیره")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("لغو")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun MainScreenPreview() {
    MaterialTheme(colorScheme = KisehColorScheme) {
        val sampleTransactions = listOf(
            TransactionEntity(id = 1, title = "خرید از سوپرمارکت", amount = 350000.0, type = "EXPENSE", category = "خرید", bankName = "بانک ملی", date = System.currentTimeMillis()),
            TransactionEntity(id = 2, title = "واریز حقوق آجر", amount = 12000000.0, type = "INCOME", category = "حقوق", bankName = "بانک ملت", date = System.currentTimeMillis())
        )
        MainScreen(
            transactions = sampleTransactions,
            onDeleteTransaction = {}
        )
    }
}
