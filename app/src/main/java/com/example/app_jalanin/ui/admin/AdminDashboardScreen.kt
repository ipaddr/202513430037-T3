package com.example.app_jalanin.ui.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AdminDashboardScreen(
    username: String? = null,
    onLogout: () -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(0) }
    var logoutTapCount by remember { mutableStateOf(0) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            AdminBottomNavigationBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                onLogoutTap = {
                    logoutTapCount++
                    if (logoutTapCount >= 7) {
                        showLogoutDialog = true
                        logoutTapCount = 0
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF8F9FA))
        ) {
            when (selectedTab) {
                0 -> AdminHomeContent(username = username ?: "Admin")
                1 -> AdminDriversContent()
                2 -> AdminPassengersContent()
                3 -> AdminLogsContent()
            }
        }
    }

    // Logout Dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Konfirmasi Logout Admin") },
            text = { Text("Apakah Anda yakin ingin keluar dari dashboard admin?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Logout")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
private fun AdminHomeContent(username: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                .background(Color.White)
                .border(width = 1.dp, color = Color(0xFFE5E7EB))
                .padding(horizontal = 24.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Dashboard Admin",
                color = Color(0xFF1F2937),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Statistics Cards
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Total Driver Card
                AdminStatCard(
                    title1 = "Total Driver",
                    title2 = "Terdaftar",
                    value = "125",
                    valueColor = Color(0xFF1F2937)
                )

                // Row of 2 cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AdminStatCard(
                        title1 = "Total Penumpang",
                        title2 = "Terdaftar",
                        value = "430",
                        valueColor = Color(0xFF1F2937),
                        modifier = Modifier.weight(1f)
                    )
                    AdminStatCard(
                        title1 = "Driver Menunggu",
                        title2 = "Verifikasi",
                        value = "8",
                        valueColor = Color(0xFFDC2626),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Penumpang Menunggu Verifikasi
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Penumpang Menunggu Verifikasi",
                    color = Color(0xFF1F2937),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFD1D5DB))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE5E7EB))
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Penumpang placeholder",
                                color = Color(0xFF4B5563),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Menunggu verifikasi",
                                color = Color(0xFF6B7280),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // Riwayat Aktivitas Admin
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Riwayat Aktivitas Admin",
                    color = Color(0xFF1F2937),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AdminActivityItem(
                        text = "Driver A disetujui",
                        dotColor = Color(0xFF10B981)
                    )
                    AdminActivityItem(
                        text = "Penumpang B dihapus",
                        dotColor = Color(0xFFEF4444)
                    )
                    AdminActivityItem(
                        text = "SIM buram ditolak",
                        dotColor = Color(0xFFF59E0B)
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminStatCard(
    title1: String,
    title2: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(120.dp)
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(12.dp),
                ambientColor = Color.Black.copy(alpha = 0.04f)
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFD1D5DB))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title1,
                color = Color(0xFF6B7280),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            Text(
                text = title2,
                color = Color(0xFF6B7280),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = valueColor,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AdminActivityItem(
    text: String,
    dotColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(dotColor)
        )
        Text(
            text = text,
            color = Color(0xFF4B5563),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AdminDriversContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Filled.DirectionsCar,
                contentDescription = null,
                tint = Color(0xFF9CA3AF),
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = "Manajemen Driver",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1F2937)
            )
            Text(
                text = "(Coming Soon)",
                color = Color(0xFF6B7280),
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun AdminPassengersContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Filled.Group,
                contentDescription = null,
                tint = Color(0xFF9CA3AF),
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = "Manajemen Penumpang",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1F2937)
            )
            Text(
                text = "(Coming Soon)",
                color = Color(0xFF6B7280),
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun AdminLogsContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Filled.Description,
                contentDescription = null,
                tint = Color(0xFF9CA3AF),
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = "Log Aktivitas",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1F2937)
            )
            Text(
                text = "(Coming Soon)",
                color = Color(0xFF6B7280),
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun AdminBottomNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onLogoutTap: () -> Unit
) {
    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 0.dp,
        modifier = Modifier.border(BorderStroke(1.dp, Color(0xFFE5E7EB)))
    ) {
        NavigationBarItem(
            selected = selectedTab == 0,
            onClick = {
                onTabSelected(0)
                onLogoutTap() // Secret tap for logout
            },
            icon = {
                Icon(
                    Icons.Filled.Dashboard,
                    contentDescription = null,
                    tint = if (selectedTab == 0) Color(0xFF1F2937) else Color(0xFF9CA3AF)
                )
            },
            label = {
                Text(
                    "Dashboard",
                    color = if (selectedTab == 0) Color(0xFF1F2937) else Color(0xFF9CA3AF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = Color.Transparent
            )
        )
        NavigationBarItem(
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            icon = {
                Icon(
                    Icons.Filled.DirectionsCar,
                    contentDescription = null,
                    tint = if (selectedTab == 1) Color(0xFF1F2937) else Color(0xFF9CA3AF)
                )
            },
            label = {
                Text(
                    "Driver",
                    color = if (selectedTab == 1) Color(0xFF1F2937) else Color(0xFF9CA3AF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = Color.Transparent
            )
        )
        NavigationBarItem(
            selected = selectedTab == 2,
            onClick = { onTabSelected(2) },
            icon = {
                Icon(
                    Icons.Filled.Group,
                    contentDescription = null,
                    tint = if (selectedTab == 2) Color(0xFF1F2937) else Color(0xFF9CA3AF)
                )
            },
            label = {
                Text(
                    "Penumpang",
                    color = if (selectedTab == 2) Color(0xFF1F2937) else Color(0xFF9CA3AF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = Color.Transparent
            )
        )
        NavigationBarItem(
            selected = selectedTab == 3,
            onClick = { onTabSelected(3) },
            icon = {
                Icon(
                    Icons.Filled.Description,
                    contentDescription = null,
                    tint = if (selectedTab == 3) Color(0xFF1F2937) else Color(0xFF9CA3AF)
                )
            },
            label = {
                Text(
                    "Log",
                    color = if (selectedTab == 3) Color(0xFF1F2937) else Color(0xFF9CA3AF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = Color.Transparent
            )
        )
    }
}

