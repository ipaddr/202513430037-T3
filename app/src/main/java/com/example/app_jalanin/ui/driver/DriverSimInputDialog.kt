package com.example.app_jalanin.ui.driver

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.app_jalanin.data.model.SimType

/**
 * Dialog untuk input SIM driver dengan text input dan questionnaire
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverSimInputDialog(
    currentSimCertifications: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (List<SimType>) -> Unit
) {
    // Parse current SIM certifications
    val currentSims = remember(currentSimCertifications) {
        currentSimCertifications?.split(",")?.mapNotNull { 
            try { SimType.valueOf(it.trim()) } catch (e: Exception) { null }
        } ?: emptyList()
    }
    
    var selectedSimA by remember { mutableStateOf(currentSims.contains(SimType.SIM_A)) }
    var selectedSimC by remember { mutableStateOf(currentSims.contains(SimType.SIM_C)) }
    
    // Text input untuk nomor SIM
    var simANumber by remember { mutableStateOf("") }
    var simCNumber by remember { mutableStateOf("") }
    
    // Questionnaire answers
    var question1Answer by remember { mutableStateOf("") }
    var question2Answer by remember { mutableStateOf("") }
    var question3Answer by remember { mutableStateOf("") }
    
    val scrollState = rememberScrollState()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.DriveEta, contentDescription = null)
                Text("Input SIM Driver")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Info card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Ketentuan SIM",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "• SIM A: Untuk mengendarai mobil",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "• SIM C: Untuk mengendarai motor",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "• Anda dapat memiliki kedua SIM (A dan C)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                
                // SIM Selection
                Text(
                    text = "Pilih Jenis SIM yang Anda Miliki",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedSimA,
                        onClick = { selectedSimA = !selectedSimA },
                        label = { Text("SIM A (Mobil)") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedSimC,
                        onClick = { selectedSimC = !selectedSimC },
                        label = { Text("SIM C (Motor)") },
                        modifier = Modifier.weight(1f)
                    )
                }
                
                // SIM A Number Input
                if (selectedSimA) {
                    OutlinedTextField(
                        value = simANumber,
                        onValueChange = { simANumber = it },
                        label = { Text("Nomor SIM A") },
                        placeholder = { Text("Contoh: 1234567890123456") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                
                // SIM C Number Input
                if (selectedSimC) {
                    OutlinedTextField(
                        value = simCNumber,
                        onValueChange = { simCNumber = it },
                        label = { Text("Nomor SIM C") },
                        placeholder = { Text("Contoh: 1234567890123456") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                
                // Questionnaire Section
                Divider()
                
                Text(
                    text = "Pertanyaan Wajib",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                
                // Question 1
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "1. Berapa lama pengalaman mengemudi Anda?",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        OutlinedTextField(
                            value = question1Answer,
                            onValueChange = { question1Answer = it },
                            placeholder = { Text("Contoh: 5 tahun, 3 tahun, dll") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
                
                // Question 2
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "2. Apakah SIM Anda masih berlaku? (Ya/Tidak) Jika tidak, kapan masa berlaku berakhir?",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        OutlinedTextField(
                            value = question2Answer,
                            onValueChange = { question2Answer = it },
                            placeholder = { Text("Contoh: Ya, masih berlaku sampai 2025") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = false,
                            minLines = 2,
                            maxLines = 3
                        )
                    }
                }
                
                // Question 3
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "3. Apakah Anda pernah terlibat dalam kecelakaan lalu lintas dalam 2 tahun terakhir? (Ya/Tidak) Jika ya, jelaskan singkat.",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        OutlinedTextField(
                            value = question3Answer,
                            onValueChange = { question3Answer = it },
                            placeholder = { Text("Contoh: Tidak, atau Ya - kecelakaan ringan di parkiran") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = false,
                            minLines = 2,
                            maxLines = 3
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val selectedSims = mutableListOf<SimType>()
                    if (selectedSimA) {
                        if (simANumber.isNotBlank()) {
                            selectedSims.add(SimType.SIM_A)
                        }
                    }
                    if (selectedSimC) {
                        if (simCNumber.isNotBlank()) {
                            selectedSims.add(SimType.SIM_C)
                        }
                    }
                    
                    // Validate questionnaire
                    if (question1Answer.isNotBlank() && 
                        question2Answer.isNotBlank() && 
                        question3Answer.isNotBlank() &&
                        selectedSims.isNotEmpty()) {
                        onConfirm(selectedSims)
                    }
                },
                enabled = (selectedSimA && simANumber.isNotBlank() || !selectedSimA) &&
                          (selectedSimC && simCNumber.isNotBlank() || !selectedSimC) &&
                          question1Answer.isNotBlank() &&
                          question2Answer.isNotBlank() &&
                          question3Answer.isNotBlank() &&
                          (selectedSimA || selectedSimC)
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
