package com.example.app_jalanin.ui.driver

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.app_jalanin.data.local.entity.IncomeHistory
import com.example.app_jalanin.data.local.entity.PaymentHistory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * Driver Payment History Screen
 * Displays list of payments received by the driver
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverPaymentHistoryScreen(
    driverEmail: String,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")) }
    
    var payments by remember { mutableStateOf<List<PaymentHistory>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    LaunchedEffect(driverEmail) {
        try {
            withContext(Dispatchers.IO) {
                // Load payments where driver is the receiver
                val allPaymentsFlow = database.paymentHistoryDao()
                    .getAllPaymentsFlow()
                val allPayments = allPaymentsFlow.first()
                    .filter { 
                        it.driverEmail == driverEmail && it.driverIncome > 0
                    }
                
                payments = allPayments.sortedByDescending { it.createdAt }
                isLoading = false
            }
        } catch (e: Exception) {
            android.util.Log.e("DriverPaymentHistory", "Error loading payments: ${e.message}", e)
            isLoading = false
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
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (payments.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Default.Payment,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color(0xFF9E9E9E)
                    )
                    Text(
                        text = "Tidak ada riwayat pembayaran",
                        fontSize = 16.sp,
                        color = Color(0xFF757575)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(payments) { payment ->
                    DriverPaymentHistoryCard(
                        payment = payment,
                        dateFormat = dateFormat
                    )
                }
            }
        }
    }
}

@Composable
private fun DriverPaymentHistoryCard(
    payment: PaymentHistory,
    dateFormat: SimpleDateFormat
) {
    val formatRupiah = remember {
        { amount: Int ->
            val formatter = java.text.NumberFormat.getCurrencyInstance(java.util.Locale("id", "ID"))
            formatter.format(amount)
        }
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
            // Header: Vehicle Name & Role Badge
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
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF4CAF50).copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "Completed",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF4CAF50)
                    )
                }
            }
            
            // Payment Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Amount:",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = formatRupiah(payment.driverIncome),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )
            }
            
            // Payment Method
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    when (payment.paymentMethod) {
                        "M-Banking" -> Icons.Default.PhoneAndroid
                        "ATM" -> Icons.Default.AccountBalance
                        "Tunai" -> Icons.Default.Money
                        else -> Icons.Default.Payment
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = when (payment.paymentMethod) {
                        "M-Banking" -> Color(0xFF2196F3)
                        "ATM" -> Color(0xFF4CAF50)
                        "Tunai" -> Color(0xFF9E9E9E)
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
                Text(
                    text = payment.paymentMethod,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            
            // Source (Passenger)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = Color(0xFF757575)
                )
                Text(
                    text = "From: ${payment.userEmail.split("@").firstOrNull() ?: payment.userEmail}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
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
                    modifier = Modifier.size(18.dp),
                    tint = Color(0xFF757575)
                )
                Text(
                    text = dateFormat.format(Date(payment.createdAt)),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

