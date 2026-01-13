package com.example.app_jalanin.ui.debug

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.app_jalanin.auth.AuthUtils
import com.example.app_jalanin.data.AppDatabase
import com.example.app_jalanin.data.local.entity.User
import com.example.app_jalanin.data.sync.DeleteAccountManager
import kotlinx.coroutines.launch

@Composable
fun DebugTestScreen(navController: NavController? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf("Ready to test...") }

    // Initialize managers
    val deleteManager = remember { DeleteAccountManager(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("🔧 DEBUG TEST SCREEN", style = MaterialTheme.typography.headlineMedium)

        HorizontalDivider()

        // Test 1: Force Seed Dummy User
        Button(
            onClick = {
                scope.launch {
                    try {
                        val db = AppDatabase.getDatabase(context)
                        val userDao = db.userDao()

                        // Delete all first
                        userDao.deleteAll()
                        result = "✅ Deleted all users\n"

                        // Insert dummy user
                        val user = User(
                            id = 0,
                            email = "user123@jalanin.com",
                            password = "jalanin_aja_dulu",
                            role = "penumpang",
                            fullName = "Test Dummy",
                            phoneNumber = "081234567890",
                            createdAt = System.currentTimeMillis(),
                            synced = false
                        )
                        val id = userDao.insert(user)
                        result += "✅ Inserted user ID: $id\n"

                        // Verify
                        val inserted = userDao.getUserByEmail("user123@jalanin.com")
                        if (inserted != null) {
                            result += "✅ VERIFIED:\n"
                            result += "  Email: ${inserted.email}\n"
                            result += "  Password: ${inserted.password}\n"
                            result += "  Role: ${inserted.role}\n"
                        } else {
                            result += "❌ FAILED: User not found after insert!"
                        }
                    } catch (e: Exception) {
                        result = "❌ ERROR: ${e.message}"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("1️⃣ FORCE SEED DUMMY USER")
        }

        // Test 2: Test Login Query
        Button(
            onClick = {
                scope.launch {
                    try {
                        val db = AppDatabase.getDatabase(context)
                        val userDao = db.userDao()

                        val email = "user123@jalanin.com"
                        val password = "jalanin_aja_dulu"
                        val role = "penumpang"

                        result = "🔍 Testing login query...\n"
                        result += "  Email: $email\n"
                        result += "  Password: $password\n"
                        result += "  Role: $role\n\n"

                        // Test getUserByEmail
                        val user = userDao.getUserByEmail(email)
                        if (user == null) {
                            result += "❌ getUserByEmail returned NULL!\n"
                        } else {
                            result += "✅ getUserByEmail SUCCESS:\n"
                            result += "  DB Email: ${user.email}\n"
                            result += "  DB Password: ${user.password}\n"
                            result += "  DB Role: ${user.role}\n\n"

                            // Check matches
                            if (user.password == password) {
                                result += "✅ Password MATCH\n"
                            } else {
                                result += "❌ Password MISMATCH\n"
                                result += "  Expected: ${user.password}\n"
                                result += "  Got: $password\n"
                            }

                            if (user.role.equals(role, ignoreCase = true)) {
                                result += "✅ Role MATCH\n"
                            } else {
                                result += "❌ Role MISMATCH\n"
                                result += "  Expected: ${user.role}\n"
                                result += "  Got: $role\n"
                            }
                        }

                        // Test login query
                        result += "\n🔍 Testing login() query...\n"
                        val loginResult = userDao.login(email, password, role)
                        if (loginResult == null) {
                            result += "❌ login() returned NULL!\n"
                        } else {
                            result += "✅ login() SUCCESS: ${loginResult.email}\n"
                        }
                    } catch (e: Exception) {
                        result = "❌ ERROR: ${e.message}\n${e.stackTraceToString()}"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("2️⃣ TEST LOGIN QUERY")
        }

        // Test 3: List All Users
        Button(
            onClick = {
                scope.launch {
                    try {
                        val db = AppDatabase.getDatabase(context)
                        val userDao = db.userDao()

                        val users = userDao.getAllUsers()
                        result = "📋 Total users in DB: ${users.size}\n\n"

                        if (users.isEmpty()) {
                            result += "⚠️ Database is EMPTY!\n"
                        } else {
                            users.forEachIndexed { index, user ->
                                result += "${index + 1}. ${user.email}\n"
                                result += "   Password: ${user.password}\n"
                                result += "   Role: ${user.role}\n"
                                result += "   ID: ${user.id}\n\n"
                            }
                        }
                    } catch (e: Exception) {
                        result = "❌ ERROR: ${e.message}"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("3️⃣ LIST ALL USERS")
        }

        HorizontalDivider()

        // DELETE FUNCTIONS
        Text(
            text = "🗑️ DELETE OPERATIONS",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // Input email to delete
        var emailToDelete by remember { mutableStateOf("") }

        OutlinedTextField(
            value = emailToDelete,
            onValueChange = { emailToDelete = it },
            label = { Text("Email to delete") },
            placeholder = { Text("user@example.com") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Delete by email (COMPLETE - Local + Firebase)
        Button(
            onClick = {
                scope.launch {
                    result = "⏳ Deleting account completely...\n"
                    val deleteResult = deleteManager.deleteAccountCompletely(emailToDelete)
                    result = deleteResult.fold(
                        onSuccess = { it },
                        onFailure = { "❌ Error: ${it.message}" }
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
            enabled = emailToDelete.isNotBlank()
        ) {
            Text("🗑️ DELETE USER (Local + Firebase)", color = Color.White)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Delete ALL LOCAL users
        Button(
            onClick = {
                scope.launch {
                    result = "⏳ Deleting all local users...\n"
                    val deleteResult = deleteManager.deleteAllLocalUsers()
                    result = deleteResult.getOrDefault("Done")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722))
        ) {
            Text("🗑️ DELETE ALL LOCAL USERS", color = Color.White)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Delete ALL FIREBASE users (DANGER!)
        var showFirebaseDeleteConfirm by remember { mutableStateOf(false) }

        Button(
            onClick = { showFirebaseDeleteConfirm = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))
        ) {
            Text("🔥 DELETE ALL FIREBASE USERS", color = Color.White)
        }

        // Confirmation dialog
        if (showFirebaseDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showFirebaseDeleteConfirm = false },
                title = { Text("⚠️ DANGER!") },
                text = { Text("This will delete ALL users from Firebase Firestore! This action cannot be undone. Are you sure?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showFirebaseDeleteConfirm = false
                            scope.launch {
                                result = "⏳ Deleting all Firebase users...\n"
                                val deleteResult = deleteManager.deleteAllFirestoreUsers()
                                result = deleteResult.getOrDefault("Done")
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                    ) {
                        Text("YES, DELETE ALL")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showFirebaseDeleteConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        HorizontalDivider()

        // MANUAL DELETE TEST
        Text(
            text = "🧪 MANUAL DELETE TEST",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        var testEmail by remember { mutableStateOf("carel123") }

        OutlinedTextField(
            value = testEmail,
            onValueChange = { testEmail = it },
            label = { Text("Email to test delete") },
            placeholder = { Text("carel123") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                scope.launch {
                    result = "⏳ Testing manual delete for: $testEmail\n"
                    try {
                        val db = AppDatabase.getDatabase(context)
                        val userDao = db.userDao()

                        // Check before delete
                        val userBefore = userDao.getUserByEmail(testEmail)
                        result += if (userBefore != null) {
                            "✅ User EXISTS before delete\n"
                        } else {
                            "⚠️ User NOT FOUND in database\n"
                        }

                        if (userBefore != null) {
                            // Attempt delete
                            result += "⏳ Attempting delete (LOCAL ONLY)...\n"
                            userDao.deleteByEmail(testEmail)

                            // Verify after delete
                            kotlinx.coroutines.delay(100) // Small delay
                            val userAfter = userDao.getUserByEmail(testEmail)

                            result += if (userAfter == null) {
                                "✅ DELETE SUCCESSFUL - User removed from LOCAL database\n"
                            } else {
                                "❌ DELETE FAILED - User still exists!\n" +
                                "   ID: ${userAfter.id}\n" +
                                "   Email: ${userAfter.email}\n"
                            }

                            // Count total users
                            val totalUsers = userDao.getAllUsers().size
                            result += "📊 Total users in database: $totalUsers"
                        }
                    } catch (e: Exception) {
                        result = "❌ Error: ${e.message}\n${e.stackTraceToString()}"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00)),
            enabled = testEmail.isNotBlank()
        ) {
            Text("🧪 TEST DELETE (Local Only)", color = Color.White)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // COMPLETE DELETE BUTTON
        Button(
            onClick = {
                scope.launch {
                    result = "⏳ COMPLETE DELETE for: $testEmail\n"
                    result += "This will delete from:\n"
                    result += "1. Local Database\n"
                    result += "2. Firebase Firestore\n"
                    result += "3. Firebase Authentication (if logged in)\n\n"

                    try {
                        val deleteManager = com.example.app_jalanin.data.sync.DeleteAccountManager(context)
                        val deleteResult = deleteManager.deleteAccountCompletely(testEmail)

                        if (deleteResult.isSuccess) {
                            result += "${deleteResult.getOrNull()}\n"

                            // Verify deletion
                            kotlinx.coroutines.delay(200)
                            val db = AppDatabase.getDatabase(context)
                            val verifyUser = db.userDao().getUserByEmail(testEmail)

                            result += if (verifyUser == null) {
                                "\n✅ VERIFICATION: User removed from local DB"
                            } else {
                                "\n⚠️ WARNING: User still in local DB!"
                            }

                            val totalUsers = db.userDao().getAllUsers().size
                            result += "\n📊 Total users in database: $totalUsers"

                            // Add ghost account warning
                            result += "\n\n⚠️ IMPORTANT:"
                            result += "\nJika user TIDAK SEDANG LOGIN, Firebase Auth"
                            result += "\ntidak bisa dihapus dari client-side!"
                            result += "\nIni akan create GHOST ACCOUNT."
                            result += "\n\n💡 SOLUTION untuk Ghost Account:"
                            result += "\n1. Login dengan akun ini terlebih dahulu"
                            result += "\n2. Lalu delete dari Settings/Akun"
                            result += "\n3. ATAU: Gunakan Firebase Console untuk"
                            result += "\n   manual delete dari Authentication"
                        } else {
                            result += "❌ DELETE FAILED:\n${deleteResult.exceptionOrNull()?.message}"
                        }
                    } catch (e: Exception) {
                        result = "❌ Error: ${e.message}\n${e.stackTraceToString()}"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)), // Darker red
            enabled = testEmail.isNotBlank()
        ) {
            Text("🗑️ COMPLETE DELETE (All Sources)", color = Color.White)
        }

        HorizontalDivider()

        // SYNC CONTROL
        Text(
            text = "🔄 SYNC CONTROL",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Button(
            onClick = {
                scope.launch {
                    result = "⏳ Restarting real-time sync...\n"
                    try {
                        com.example.app_jalanin.data.remote.FirestoreSyncManager.stopRealtimeSync()
                        kotlinx.coroutines.delay(500)
                        com.example.app_jalanin.data.remote.FirestoreSyncManager.startRealtimeSync(context)
                        result += "✅ Real-time sync restarted!\n"
                        result += "Now try deleting a user from Firebase Console and wait 2-3 seconds."
                    } catch (e: Exception) {
                        result = "❌ Error: ${e.message}"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
        ) {
            Text("🔄 FORCE RESTART SYNC", color = Color.White)
        }

        HorizontalDivider()

        // Navigation button
        navController?.let {
            Button(
                onClick = { navController.navigate("login") },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("✅ GO TO LOGIN SCREEN")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        HorizontalDivider()

        // Result Display
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Text(
                text = result,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

