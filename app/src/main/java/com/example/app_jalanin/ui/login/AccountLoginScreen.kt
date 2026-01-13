package com.example.app_jalanin.ui.login

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.example.app_jalanin.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.verticalScroll
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.app_jalanin.data.auth.UserRole

@Composable
fun AccountLoginScreen(
    onRegisterClick: () -> Unit,
    onLoginSuccess: (String, String) -> Unit,
    onDebugScreenClick: () -> Unit = {},
    onAdminLoginClick: () -> Unit = {},
    vm: LoginViewModel = viewModel()
) {
    val context = LocalContext.current
    var loginTriggered by remember { mutableStateOf(false) }
    var dummyAccountIndex by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        vm.selectedRole.value = UserRole.PENUMPANG
        vm.ensureDummyPassenger()
        vm.ensureDummyOwner()
        vm.ensureDummyDriver()
    }

    val success by vm.loginSuccess.collectAsStateWithLifecycle(initialValue = null)
    val lastUser by vm.lastEmail.collectAsStateWithLifecycle()
    val lastRole by vm.lastRole.collectAsStateWithLifecycle()
    val errorMessage by vm.errorMessage.collectAsStateWithLifecycle()
    val showResendButton by vm.showResendButton.collectAsStateWithLifecycle()
    val resendCooldownSeconds: Int by vm.resendCooldownSeconds.collectAsStateWithLifecycle()

    data class DummyAccount(
        val email: String,
        val password: String,
        val role: UserRole,
        val label: String,
        val emoji: String
    )

    val dummyAccounts = remember {
        listOf(
            DummyAccount(
                email = "user123@jalanin.com",
                password = "jalanin_aja_dulu",
                role = UserRole.PENUMPANG,
                label = "Dummy Penumpang",
                emoji = "👤"
            ),
            DummyAccount(
                email = "owner123@jalanin.com",
                password = "owner_rental_2024",
                role = UserRole.PEMILIK_KENDARAAN,
                label = "Dummy Owner",
                emoji = "👔"
            ),
            DummyAccount(
                email = "driver123@jalanin.com",
                password = "driver_jalan_2024",
                role = UserRole.DRIVER,
                label = "Dummy Driver",
                emoji = "🚗"
            )
        )
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(success, loginTriggered) {
        if (loginTriggered && success == true && lastUser != null && lastRole != null) {
            val roleName = lastRole!!.name.lowercase().replace('_', ' ')
            Toast.makeText(context, "Login berhasil: ${lastUser} sebagai $roleName", Toast.LENGTH_SHORT).show()
            onLoginSuccess(lastUser!!, roleName)
            loginTriggered = false
        } else if (loginTriggered && success == false) {
            loginTriggered = false
        }
    }

    AccountLoginContent(
        modifier = Modifier,
        onRegisterClick = onRegisterClick,
        onLoginClick = {
            loginTriggered = true
            vm.login()
        },
        onEmailChanged = { vm.email.value = it },
        onPasswordChanged = { vm.password.value = it },
        onRoleChanged = { roleStr ->
            vm.selectedRole.value = when (roleStr) {
                "penumpang" -> UserRole.PENUMPANG
                "driver" -> UserRole.DRIVER
                "pemilik" -> UserRole.PEMILIK_KENDARAAN
                else -> UserRole.PENUMPANG
            }
        },
        onDebugAutoFill = {
            val account = dummyAccounts[dummyAccountIndex]
            vm.email.value = account.email
            vm.password.value = account.password
            vm.selectedRole.value = account.role

            Toast.makeText(
                context,
                "${account.emoji} Auto-filled: ${account.label}\n📧 ${account.email}",
                Toast.LENGTH_SHORT
            ).show()

            dummyAccountIndex = (dummyAccountIndex + 1) % dummyAccounts.size
        },
        onDebugRecreateDummy = {
            vm.forceRecreateDummyUser()
            Toast.makeText(context, "🔄 Recreating dummy accounts...", Toast.LENGTH_SHORT).show()
        },
        onDebugScreenClick = onDebugScreenClick,
        onAdminLoginClick = onAdminLoginClick,
        emailFromVm = vm.email.collectAsStateWithLifecycle().value,
        passwordFromVm = vm.password.collectAsStateWithLifecycle().value,
        roleFromVm = vm.selectedRole.collectAsStateWithLifecycle().value,
        showResendButton = showResendButton,
        resendCooldownSeconds = resendCooldownSeconds,
        onResendVerification = { vm.resendVerificationEmail() },
        currentDummyAccount = dummyAccounts[dummyAccountIndex]
    )
}

@Composable
private fun AccountLoginContent(
    modifier: Modifier = Modifier,
    onRegisterClick: () -> Unit = {},
    onLoginClick: (String) -> Unit = {},
    onEmailChanged: (String) -> Unit = {},
    onPasswordChanged: (String) -> Unit = {},
    onRoleChanged: (String) -> Unit = {},
    onDebugAutoFill: () -> Unit = {},
    onDebugRecreateDummy: () -> Unit = {},
    onDebugScreenClick: () -> Unit = {},
    onAdminLoginClick: () -> Unit = {},
    emailFromVm: String = "",
    passwordFromVm: String = "",
    roleFromVm: UserRole = UserRole.PENUMPANG,
    showResendButton: Boolean = false,
    resendCooldownSeconds: Int = 0,
    onResendVerification: () -> Unit = {},
    currentDummyAccount: Any? = null
) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var role by remember { mutableStateOf("penumpang") }
    var adminTapCount by remember { mutableStateOf(0) }

    LaunchedEffect(emailFromVm) {
        if (emailFromVm.isNotEmpty()) email = emailFromVm
    }
    LaunchedEffect(passwordFromVm) {
        if (passwordFromVm.isNotEmpty()) password = passwordFromVm
    }
    LaunchedEffect(roleFromVm) {
        role = when (roleFromVm) {
            UserRole.PENUMPANG -> "penumpang"
            UserRole.DRIVER -> "driver"
            UserRole.PEMILIK_KENDARAAN -> "pemilik"
            UserRole.ADMIN -> "admin"
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Logo
        Image(
            painter = painterResource(id = R.drawable.jalanin_logo),
            contentDescription = "JalanIn Logo",
            modifier = Modifier
                .size(120.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                ) {
                    adminTapCount++
                    if (adminTapCount >= 7) {
                        Toast.makeText(context, "🔐 Admin Access Unlocked", Toast.LENGTH_SHORT).show()
                        onAdminLoginClick()
                        adminTapCount = 0
                    } else if (adminTapCount >= 4) {
                        Toast.makeText(context, "🤫 ${7 - adminTapCount} more taps...", Toast.LENGTH_SHORT).show()
                    }
                }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Header
        Text(
            text = "Masuk ke JalanIn",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Subtitle with emoji
        Text(
            text = "Masuk sebagai:",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Role selector with emoji
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RoleOptionItemEmoji(
                emoji = "🚗",
                title = "Driver",
                selected = role == "driver",
                onClick = { role = "driver"; onRoleChanged("driver") }
            )
            RoleOptionItemEmoji(
                emoji = "👤",
                title = "Penumpang",
                selected = role == "penumpang",
                onClick = { role = "penumpang"; onRoleChanged("penumpang") }
            )
            RoleOptionItemEmoji(
                emoji = "👔",
                title = "Pemilik Kendaraan",
                selected = role == "pemilik",
                onClick = { role = "pemilik"; onRoleChanged("pemilik") }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Email field
        OutlinedTextField(
            value = email,
            onValueChange = { email = it; onEmailChanged(it) },
            label = { Text("📧 Email") },
            placeholder = { Text("masukkan email") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Password field
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; onPasswordChanged(it) },
            label = { Text("🔒 Password") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = if (passwordVisible) "Sembunyikan password" else "Tampilkan password"
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // DEBUG SECTION
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedButton(
                onClick = onDebugAutoFill,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "🔧 Auto-fill Akun Dummy",
                    fontSize = 12.sp
                )
            }
            Text(
                text = "Klik beberapa kali untuk ganti akun dummy",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Login button
        Button(
            onClick = { onLoginClick(role) },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Masuk")
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Register link
        TextButton(onClick = onRegisterClick) {
            Text(
                text = "Belum punya akun? Daftar sekarang",
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (showResendButton) {
            Spacer(modifier = Modifier.height(8.dp))
            val isOnCooldown = resendCooldownSeconds > 0

            OutlinedButton(
                onClick = onResendVerification,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isOnCooldown
            ) {
                Text(
                    text = if (isOnCooldown) {
                        "⏱️ Tunggu ${resendCooldownSeconds}s"
                    } else {
                        "📧 Kirim Ulang Email Verifikasi"
                    },
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Debug Screen Button
        OutlinedButton(
            onClick = onDebugScreenClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("🔧 Debug Screen", fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun RoleOptionItemEmoji(
    emoji: String,
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = if (selected) 4.dp else 0.dp,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(
                2.dp,
                MaterialTheme.colorScheme.primary
            )
        } else {
            androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .clickable { onClick() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick
            )
            Text(
                text = emoji,
                fontSize = 28.sp
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

