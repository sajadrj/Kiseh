package com.example.kiseh

import android.Manifest
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.kiseh.data.local.entity.AppDatabase
import com.example.kiseh.data.local.entity.TransactionEntity
import com.example.kiseh.util.SmsContentObserver
import com.example.kiseh.util.SmsReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

// پالت رنگی جذاب و مدرن مناسب اپلیکیشن مالی (کیسه)
private val KisehColorScheme = lightColorScheme(
    primary = Color(0xFF0F766E),         // سبز مایل به آبی (Teal/Emerald)
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF134E4A),
    secondary = Color(0xFF4F46E5),       // بنفش/آبی مدرن (Indigo)
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0E7FF),
    onSecondaryContainer = Color(0xFF3730A3),
    background = Color(0xFFF8FAFC),      // پس‌زمینه بسیار ملایم
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
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "افزودن تراکنش")
                            }
                        }
                    ) { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding)) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // نمایش لیست تراکنش‌ها، آمار، نمودارها و تفکیک بانک‌ها
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
                                    onConfirm = { title, amount, type, bankName ->
                                        coroutineScope.launch(Dispatchers.IO) {
                                            transactionDao.insertTransaction(
                                                TransactionEntity(
                                                    title = title,
                                                    amount = amount,
                                                    type = type,
                                                    category = "دستی",
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

@Composable
fun MainScreen(
    transactions: List<TransactionEntity>,
    onDeleteTransaction: (Long) -> Unit
) {
    var selectedBank by remember { mutableStateOf("همه") }

    val totalIncome = transactions.filter { it.type == "INCOME" }.sumOf { it.amount }
    val totalExpense = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
    val netBalance = totalIncome - totalExpense

    val availableBanks = listOf("همه") + transactions.map { it.bankName }.distinct()
    val filteredTransactions = if (selectedBank == "همه") transactions else transactions.filter { it.bankName == selectedBank }

    // تفکیک مبالغ بر اساس بانک برای نمودار/آمار
    val bankTotals = transactions.groupBy { it.bankName }.mapValues { entry ->
        val inc = entry.value.filter { it.type == "INCOME" }.sumOf { it.amount }
        val exp = entry.value.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        inc - exp
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "کیف پول کیسه - مدیریت تراکنش‌ها",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        // کارت خلاصه آمار و مجموع مبالغ
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                Spacer(modifier = Modifier.height(4.dp))
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

        // بخش آمار و نمودار تفکیک بانک‌ها
        if (bankTotals.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "📊 آمار و نمودار تفکیک بانک‌ها",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
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
                            Spacer(modifier = Modifier.height(2.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = if (balance >= 0) Color(0xFF10B981) else Color(0xFFF87171),
                            )
                        }
                    }
                }
            }
        }

        // نوار انتخاب بانک برای تفکیک (Filter Chips)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            availableBanks.forEach { bank ->
                FilterChip(
                    selected = selectedBank == bank,
                    onClick = { selectedBank = bank },
                    label = { Text(bank) }
                )
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = tx.title, style = MaterialTheme.typography.bodyLarge)
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = tx.bankName,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
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
fun AddTransactionDialog(onDismiss: () -> Unit, onConfirm: (String, Double, String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }
    var isIncome by remember { mutableStateOf(false) }
    var bankName by remember { mutableStateOf("بانک ملی") }

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
    var expanded by remember { mutableStateOf(false) }

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

                // انتخاب بانک
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("بانک: $bankName")
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.heightIn(max = 250.dp)
                    ) {
                        banks.forEach { b ->
                            DropdownMenuItem(
                                text = { Text(b) },
                                onClick = {
                                    bankName = b
                                    expanded = false
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
                        onConfirm(title, amount, if (isIncome) "INCOME" else "EXPENSE", bankName)
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

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun MainScreenPreview() {
    MaterialTheme(colorScheme = KisehColorScheme) {
        val sampleTransactions = listOf(
            TransactionEntity(id = 1, title = "خرید از سوپرمارکت", amount = 350000.0, type = "EXPENSE", category = "خرید", bankName = "بانک ملی", date = System.currentTimeMillis()),
            TransactionEntity(id = 2, title = "واریز حقوق آذر", amount = 12000000.0, type = "INCOME", category = "حقوق", bankName = "بانک ملت", date = System.currentTimeMillis())
        )
        MainScreen(
            transactions = sampleTransactions,
            onDeleteTransaction = {}
        )
    }
}
