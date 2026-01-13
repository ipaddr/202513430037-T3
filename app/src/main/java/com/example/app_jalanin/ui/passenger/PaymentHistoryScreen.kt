package com.example.app_jalanin.ui.passenger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.app_jalanin.data.AppDatabase
import com.example.app_jalanin.data.local.entity.PaymentHistory
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentHistoryScreen(
    userEmail: String,
    onBackClick: () -> Unit,
    onNavigateToDriverTracking: ((String) -> Unit)? = null // rentalId
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    
    // State for showing receipt dialog
    var selectedPaymentForReceipt by remember { mutableStateOf<PaymentHistory?>(null) }
    
    // Filter to only show M-Banking payments (exclude cash)
    val paymentHistoryFlow = remember(userEmail) {
        android.util.Log.d("PaymentHistoryScreen", "🔍 Querying M-Banking payment history for userEmail: $userEmail")
        database.paymentHistoryDao().getPaymentHistoryByUser(userEmail)
    }
    val allPayments = paymentHistoryFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    
    // Filter to only M-Banking payments and sort by date (latest first)
    val paymentHistory = remember(allPayments.value) {
        allPayments.value
            .filter { it.paymentMethod == "M-Banking" || it.paymentMethod == "MBANKING" }
            .sortedByDescending { it.createdAt }
    }
    
    var totalSpent by remember { mutableStateOf(0) }
    
    LaunchedEffect(userEmail) {
        android.util.Log.d("PaymentHistoryScreen", "💰 Calculating total spent for userEmail: $userEmail")
        totalSpent = database.paymentHistoryDao().getTotalSpent(userEmail) ?: 0
        android.util.Log.d("PaymentHistoryScreen", "💰 Total spent: $totalSpent")
    }
    
    // Debug: Log payment history count
    LaunchedEffect(paymentHistory.size) {
        android.util.Log.d("PaymentHistoryScreen", "📊 Payment history count: ${paymentHistory.size}")
        if (paymentHistory.isNotEmpty()) {
            paymentHistory.forEach { payment ->
                android.util.Log.d("PaymentHistoryScreen", "  - Payment ID: ${payment.id}, userEmail: ${payment.userEmail}, amount: ${payment.amount}")
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Riwayat Pembayaran") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            // Back to Home button
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Button(
                    onClick = onBackClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Default.Home,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Back to Home",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Total Spent Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Total Pengeluaran",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = formatRupiah(totalSpent),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            // Payment History List
            if (paymentHistory.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Payment,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                        Text(
                            text = "Belum ada riwayat pembayaran",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp), // Extra bottom padding for button
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Show latest payment first with expanded receipt view
                    if (paymentHistory.isNotEmpty()) {
                        item {
                            PaymentReceiptCard(
                                payment = paymentHistory.first(),
                                userEmail = userEmail,
                                onNavigateToDriverTracking = onNavigateToDriverTracking
                            )
                        }
                        
                        // Show remaining payments as cards
                        if (paymentHistory.size > 1) {
                            items(paymentHistory.drop(1)) { payment ->
                                PaymentHistoryCard(
                                    payment = payment,
                                    onViewReceipt = { selectedPayment ->
                                        selectedPaymentForReceipt = selectedPayment
                                    }
                                )
                            }
                        }
                    } else {
                        items(paymentHistory) { payment ->
                            PaymentHistoryCard(
                                payment = payment,
                                onViewReceipt = { selectedPayment ->
                                    selectedPaymentForReceipt = selectedPayment
                                }
                            )
                        }
                    }
                }
            }
        }
        
        // Receipt Dialog (Bottom Sheet style)
        selectedPaymentForReceipt?.let { payment ->
            ModalBottomSheet(
                onDismissRequest = { selectedPaymentForReceipt = null },
                modifier = Modifier.fillMaxHeight(0.9f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Payment Receipt",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { selectedPaymentForReceipt = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    PaymentReceiptCard(
                        payment = payment,
                        userEmail = userEmail,
                        onNavigateToDriverTracking = onNavigateToDriverTracking
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun PaymentHistoryCard(
    payment: PaymentHistory,
    onViewReceipt: (PaymentHistory) -> Unit = {}
) {
    val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
    val paymentMethodIcon = when (payment.paymentMethod) {
        "M-Banking" -> Icons.Default.PhoneAndroid
        "ATM" -> Icons.Default.AccountBalance
        "Tunai" -> Icons.Default.Money
        else -> Icons.Default.Payment
    }
    
    val paymentMethodColor = when (payment.paymentMethod) {
        "M-Banking" -> Color(0xFF2196F3)
        "ATM" -> Color(0xFF4CAF50)
        "Tunai" -> Color(0xFF9E9E9E)
        else -> MaterialTheme.colorScheme.primary
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Vehicle Name & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = payment.vehicleName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    // Role Badge
                    if (payment.driverEmail != null && payment.driverIncome > 0) {
                        FilterChip(
                            selected = true,
                            onClick = {},
                            label = { Text("driver", fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color(0xFF4CAF50),
                                labelColor = Color.White
                            ),
                            modifier = Modifier.height(24.dp)
                        )
                    } else if (payment.ownerEmail.isNotEmpty()) {
                        FilterChip(
                            selected = true,
                            onClick = {},
                            label = { Text("owner", fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color(0xFF2196F3),
                                labelColor = Color.White
                            ),
                            modifier = Modifier.height(24.dp)
                        )
                    }
                }
                
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (payment.status) {
                        "COMPLETED" -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                        "PENDING" -> Color(0xFFFF9800).copy(alpha = 0.2f)
                        "FAILED" -> Color(0xFFF44336).copy(alpha = 0.2f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = when (payment.status) {
                            "COMPLETED" -> "Berhasil"
                            "PENDING" -> "Menunggu"
                            "FAILED" -> "Gagal"
                            else -> payment.status
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = when (payment.status) {
                            "COMPLETED" -> Color(0xFF4CAF50)
                            "PENDING" -> Color(0xFFFF9800)
                            "FAILED" -> Color(0xFFF44336)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            
            // Payment Method
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    paymentMethodIcon,
                    contentDescription = null,
                    tint = paymentMethodColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = payment.paymentMethod,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                if (payment.paymentType == "DP") {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF2196F3).copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "DP",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF2196F3)
                        )
                    }
                }
            }
            
            // Payment Recipient Info (Owner)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Pembayaran ke: ${payment.ownerEmail}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF9C27B0).copy(alpha = 0.2f),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = "Pemilik Kendaraan",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF9C27B0)
                        )
                    }
                }
            }
            
            // Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Jumlah Pembayaran",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = formatRupiah(payment.amount),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            // Date
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Text(
                    text = dateFormat.format(Date(payment.createdAt)),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            
            // Rental ID
            Text(
                text = "ID: ${payment.rentalId}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
            
            // View Payment Receipt button (only for M-Banking)
            if (payment.paymentMethod == "M-Banking" || payment.paymentMethod == "MBANKING") {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
                Button(
                    onClick = { onViewReceipt(payment) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        Icons.Default.Receipt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "View Payment Receipt",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun PaymentReceiptCard(
    payment: PaymentHistory,
    userEmail: String,
    onNavigateToDriverTracking: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
    
    // Get sender and receiver user info
    var senderName by remember { mutableStateOf("") }
    var ownerName by remember { mutableStateOf("") }
    var driverName by remember { mutableStateOf("") }
    var ownerAccount by remember { mutableStateOf("") }
    var driverAccount by remember { mutableStateOf("") }
    
    // Check if this is vehicle rental + driver case
    val isVehicleRentalWithDriver = payment.driverEmail != null && payment.driverIncome > 0 && payment.ownerIncome > 0
    
    LaunchedEffect(payment.userEmail, payment.ownerEmail, payment.driverEmail) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val sender = database.userDao().getUserByEmail(payment.userEmail)
            senderName = sender?.fullName ?: payment.userEmail
            
            val owner = database.userDao().getUserByEmail(payment.ownerEmail)
            ownerName = owner?.fullName ?: payment.ownerEmail
            ownerAccount = "ACC${payment.ownerEmail.hashCode().toString().takeLast(10).padStart(10, '0')}"
            
            if (payment.driverEmail != null) {
                val driver = database.userDao().getUserByEmail(payment.driverEmail)
                driverName = driver?.fullName ?: payment.driverEmail
                driverAccount = "ACC${payment.driverEmail.hashCode().toString().takeLast(10).padStart(10, '0')}"
            }
        }
    }
    
    // Generate mock bank account number for sender
    val senderAccount = remember(payment.userEmail) {
        "ACC${payment.userEmail.hashCode().toString().takeLast(10).padStart(10, '0')}"
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Receipt Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Receipt,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Payment Receipt",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            
            // Payment ID
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Payment ID:",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = "#${payment.id}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            // Payment Amount(s)
            if (isVehicleRentalWithDriver) {
                // Vehicle Rental + Driver: Show separate payments
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Payment to Owner
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Payment to Owner:",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                FilterChip(
                                    selected = true,
                                    onClick = {},
                                    label = { Text("owner", fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = Color(0xFF2196F3),
                                        labelColor = Color.White
                                    ),
                                    modifier = Modifier.height(20.dp)
                                )
                            }
                            Text(
                                text = formatRupiah(payment.ownerIncome),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2196F3)
                            )
                        }
                    }
                    
                    // Payment to Driver
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Payment to Driver:",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                FilterChip(
                                    selected = true,
                                    onClick = {},
                                    label = { Text("driver", fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = Color(0xFF4CAF50),
                                        labelColor = Color.White
                                    ),
                                    modifier = Modifier.height(20.dp)
                                )
                            }
                            Text(
                                text = formatRupiah(payment.driverIncome),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50)
                            )
                        }
                    }
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    
                    // Total Payment
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Total Payment:",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = formatRupiah(payment.amount),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                // Single payment (owner only or driver only)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Amount:",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = formatRupiah(payment.amount),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            // Payment Method
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Payment Method:",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color(0xFF2196F3)
                    )
                    Text(
                        text = "M-Banking",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            
            // Sender Information
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Sender:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = senderName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Account: $senderAccount",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            
            // Receiver Information
            if (isVehicleRentalWithDriver) {
                // Show both owner and driver receivers
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Owner Receiver
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Receiver (Owner):",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            FilterChip(
                                selected = true,
                                onClick = {},
                                label = { Text("owner", fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = Color(0xFF2196F3),
                                    labelColor = Color.White
                                ),
                                modifier = Modifier.height(20.dp)
                            )
                        }
                        Text(
                            text = ownerName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Account: $ownerAccount",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    
                    // Driver Receiver
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Receiver (Driver):",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            FilterChip(
                                selected = true,
                                onClick = {},
                                label = { Text("driver", fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = Color(0xFF4CAF50),
                                    labelColor = Color.White
                                ),
                                modifier = Modifier.height(20.dp)
                            )
                        }
                        Text(
                            text = driverName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Account: $driverAccount",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                // Single receiver
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Receiver:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = if (payment.driverEmail != null && payment.driverIncome > 0) driverName else ownerName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Account: ${if (payment.driverEmail != null && payment.driverIncome > 0) driverAccount else ownerAccount}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
            
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            
            // Payment Date and Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "Date & Time:",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                Text(
                    text = dateFormat.format(Date(payment.createdAt)),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            // Payment Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Status:",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (payment.status) {
                        "COMPLETED" -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                        "PENDING" -> Color(0xFFFF9800).copy(alpha = 0.2f)
                        "FAILED" -> Color(0xFFF44336).copy(alpha = 0.2f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = when (payment.status) {
                            "COMPLETED" -> "Success / Completed"
                            "PENDING" -> "Pending"
                            "FAILED" -> "Failed"
                            else -> payment.status
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (payment.status) {
                            "COMPLETED" -> Color(0xFF4CAF50)
                            "PENDING" -> Color(0xFFFF9800)
                            "FAILED" -> Color(0xFFF44336)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            
            // Track Driver button (if it's a driver rental)
            if (payment.vehicleName.contains("Driver Rental", ignoreCase = true) && 
                payment.rentalId.startsWith("DRIVER_RENT") &&
                onNavigateToDriverTracking != null) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
                Button(
                    onClick = { onNavigateToDriverTracking(payment.rentalId) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF9800) // Orange
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Track Driver",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

private fun formatRupiah(amount: Int): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    formatter.currency = java.util.Currency.getInstance("IDR")
    return formatter.format(amount).replace(",00", "")
}

