package com.example.app_jalanin.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.app_jalanin.data.local.BalanceRepository
import java.text.NumberFormat
import java.util.Locale

/**
 * Reusable balance card component for displaying m-banking balance
 * Used across all dashboards (Driver, Renter, Owner)
 */
@Composable
fun BalanceCard(
    userEmail: String,
    balanceRepository: BalanceRepository,
    modifier: Modifier = Modifier
) {
    // Observe balance changes
    val balanceFlow = remember(userEmail) {
        balanceRepository.getBalanceFlow(userEmail)
    }
    val balanceState = balanceFlow.collectAsStateWithLifecycle(initialValue = null)
    val balance = balanceState.value

    // Format balance as Rupiah
    val formattedBalance = remember(balance?.balance) {
        balance?.balance?.let {
            NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(it)
        } ?: "Rp 0"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Saldo m-Banking",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = formattedBalance,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

