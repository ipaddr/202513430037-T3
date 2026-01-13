package com.example.app_jalanin.ui.passenger

import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.app_jalanin.data.AppDatabase
import com.example.app_jalanin.data.local.entity.PaymentHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dedicated Payment Receipt Screen
 * Shows immediately after order confirmation
 * Displays payment details and has "Back to Dashboard" button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentReceiptScreen(
    paymentId: Long,
    onBackToDashboard: () -> Unit,
    onNavigateToDriverTracking: ((String) -> Unit)? = null // rentalId
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    
    var payment by remember { mutableStateOf<PaymentHistory?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Load payment by ID
    LaunchedEffect(paymentId) {
        try {
            val loadedPayment = withContext(Dispatchers.IO) {
                database.paymentHistoryDao().getPaymentById(paymentId)
            }
            payment = loadedPayment
            isLoading = false
            
            if (loadedPayment == null) {
                errorMessage = "Payment receipt not found"
                android.util.Log.e("PaymentReceiptScreen", "❌ Payment not found: $paymentId")
            } else {
                android.util.Log.d("PaymentReceiptScreen", "✅ Loaded payment: $paymentId")
            }
        } catch (e: Exception) {
            android.util.Log.e("PaymentReceiptScreen", "❌ Error loading payment: ${e.message}", e)
            errorMessage = "Error loading payment: ${e.message}"
            isLoading = false
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Payment Receipt") },
                navigationIcon = {
                    IconButton(onClick = onBackToDashboard) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            // Back to Dashboard button
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Button(
                    onClick = onBackToDashboard,
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
                        text = "Back to Dashboard",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = errorMessage ?: "Error",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onBackToDashboard) {
                            Text("Back to Dashboard")
                        }
                    }
                }
                payment != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        // Show payment receipt card
                        PaymentReceiptCard(
                            payment = payment!!,
                            userEmail = payment!!.userEmail,
                            onNavigateToDriverTracking = onNavigateToDriverTracking
                        )
                    }
                }
            }
        }
    }
}

