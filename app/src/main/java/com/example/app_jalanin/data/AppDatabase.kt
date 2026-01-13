package com.example.app_jalanin.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.app_jalanin.data.local.dao.UserDao
import com.example.app_jalanin.data.local.dao.RentalDao
import com.example.app_jalanin.data.local.dao.VehicleDao
import com.example.app_jalanin.data.local.dao.PassengerVehicleDao
import com.example.app_jalanin.data.local.dao.DriverRequestDao
import com.example.app_jalanin.data.local.entity.User
import com.example.app_jalanin.data.local.entity.Rental
import com.example.app_jalanin.data.local.entity.DriverRequest
import com.example.app_jalanin.data.local.entity.ChatChannel
import com.example.app_jalanin.data.local.entity.ChatMessage
import com.example.app_jalanin.data.local.dao.ChatChannelDao
import com.example.app_jalanin.data.local.dao.ChatMessageDao
import com.example.app_jalanin.data.local.dao.PaymentHistoryDao
import com.example.app_jalanin.data.local.dao.IncomeHistoryDao
import com.example.app_jalanin.data.local.dao.DriverProfileDao
import com.example.app_jalanin.data.local.entity.PaymentHistory
import com.example.app_jalanin.data.local.entity.IncomeHistory
import com.example.app_jalanin.data.local.entity.DriverProfile
import com.example.app_jalanin.data.local.entity.UserBalance
import com.example.app_jalanin.data.local.entity.BalanceTransaction
import com.example.app_jalanin.data.local.entity.DriverRental
import com.example.app_jalanin.data.local.dao.UserBalanceDao
import com.example.app_jalanin.data.local.dao.BalanceTransactionDao
import com.example.app_jalanin.data.local.dao.DriverRentalDao
import com.example.app_jalanin.data.model.Vehicle
import com.example.app_jalanin.data.model.PassengerVehicle

@Database(
    entities = [User::class, Rental::class, Vehicle::class, PassengerVehicle::class, DriverRequest::class, ChatChannel::class, ChatMessage::class, PaymentHistory::class, IncomeHistory::class, DriverProfile::class, UserBalance::class, BalanceTransaction::class, DriverRental::class],
    version = 26,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun rentalDao(): RentalDao
    abstract fun vehicleDao(): VehicleDao
    abstract fun passengerVehicleDao(): PassengerVehicleDao
    abstract fun driverRequestDao(): DriverRequestDao
    abstract fun chatChannelDao(): ChatChannelDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun paymentHistoryDao(): PaymentHistoryDao
    abstract fun incomeHistoryDao(): IncomeHistoryDao
    abstract fun driverProfileDao(): DriverProfileDao
    abstract fun userBalanceDao(): UserBalanceDao
    abstract fun balanceTransactionDao(): BalanceTransactionDao
    abstract fun driverRentalDao(): DriverRentalDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE users ADD COLUMN synced INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Rename username column to email
                // SQLite doesn't support ALTER TABLE RENAME COLUMN directly in older versions
                // So we create a new table and copy data

                // 1. Create new table with email column
                database.execSQL("""
                    CREATE TABLE users_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        email TEXT NOT NULL,
                        password TEXT NOT NULL,
                        role TEXT NOT NULL,
                        fullName TEXT,
                        phoneNumber TEXT,
                        createdAt INTEGER NOT NULL,
                        synced INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                // 2. Copy data from old table (username -> email)
                database.execSQL("""
                    INSERT INTO users_new (id, email, password, role, fullName, phoneNumber, createdAt, synced)
                    SELECT id, username, password, role, fullName, phoneNumber, createdAt, synced
                    FROM users
                """.trimIndent())

                // 3. Drop old table
                database.execSQL("DROP TABLE users")

                // 4. Rename new table to users
                database.execSQL("ALTER TABLE users_new RENAME TO users")

                // 5. Create unique index on email
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_users_email ON users(email)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create rentals table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS rentals (
                        id TEXT PRIMARY KEY NOT NULL,
                        userId INTEGER NOT NULL,
                        userEmail TEXT NOT NULL,
                        vehicleId TEXT NOT NULL,
                        vehicleName TEXT NOT NULL,
                        vehicleType TEXT NOT NULL,
                        startDate INTEGER NOT NULL,
                        endDate INTEGER NOT NULL,
                        durationDays INTEGER NOT NULL,
                        durationHours INTEGER NOT NULL,
                        durationMinutes INTEGER NOT NULL,
                        durationMillis INTEGER NOT NULL,
                        totalPrice INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        overtimeFee INTEGER NOT NULL DEFAULT 0,
                        isWithDriver INTEGER NOT NULL DEFAULT 0,
                        deliveryAddress TEXT NOT NULL DEFAULT '',
                        deliveryLat REAL NOT NULL DEFAULT 0.0,
                        deliveryLon REAL NOT NULL DEFAULT 0.0,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        synced INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                // Create indexes
                database.execSQL("CREATE INDEX IF NOT EXISTS index_rentals_userId ON rentals(userId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_rentals_status ON rentals(status)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_rentals_createdAt ON rentals(createdAt)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add userEmail index if not exists
                database.execSQL("CREATE INDEX IF NOT EXISTS index_rentals_userEmail ON rentals(userEmail)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Schema validation fix - no actual changes needed
                // This just updates the version number to match current schema
                android.util.Log.d("AppDatabase", "✅ Migration 5 -> 6: Schema validation update")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Schema validation fix - no actual changes needed
                // This just updates the version number to match current schema
                android.util.Log.d("AppDatabase", "✅ Migration 6 -> 7: Schema validation update")
            }
        }

         private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create vehicles table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS vehicles (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        ownerId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        type TEXT NOT NULL,
                        brand TEXT NOT NULL,
                        model TEXT NOT NULL,
                        year INTEGER NOT NULL,
                        licensePlate TEXT NOT NULL,
                        transmission TEXT NOT NULL,
                        seats INTEGER,
                        engineCapacity TEXT,
                        pricePerHour REAL NOT NULL,
                        pricePerDay REAL NOT NULL,
                        pricePerWeek REAL NOT NULL,
                        features TEXT NOT NULL,
                        status TEXT NOT NULL,
                        statusReason TEXT,
                        locationLat REAL NOT NULL,
                        locationLon REAL NOT NULL,
                        locationAddress TEXT NOT NULL,
                        imageUrl TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())

                // Create indexes for vehicles
                database.execSQL("CREATE INDEX IF NOT EXISTS index_vehicles_ownerId ON vehicles(ownerId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_vehicles_status ON vehicles(status)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_vehicles_type ON vehicles(type)")

                android.util.Log.d("AppDatabase", "✅ Migration 7 -> 8: Created vehicles table")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add driver assignment fields to vehicles table
                database.execSQL("ALTER TABLE vehicles ADD COLUMN driverId TEXT")
                database.execSQL("ALTER TABLE vehicles ADD COLUMN driverAvailability TEXT")
                
                // Add driver availability state to rentals table
                database.execSQL("ALTER TABLE rentals ADD COLUMN driverAvailability TEXT")
                database.execSQL("ALTER TABLE rentals ADD COLUMN driverId TEXT")
                database.execSQL("ALTER TABLE rentals ADD COLUMN ownerContacted INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE rentals ADD COLUMN ownerConfirmed INTEGER NOT NULL DEFAULT 0")
                
                // Add SIM certifications to users table
                database.execSQL("ALTER TABLE users ADD COLUMN simCertifications TEXT")
                
                android.util.Log.d("AppDatabase", "✅ Migration 8 -> 9: Added driver assignment fields and SIM certifications")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add driver online/offline status to users table
                database.execSQL("ALTER TABLE users ADD COLUMN isOnline INTEGER NOT NULL DEFAULT 0")
                
                android.util.Log.d("AppDatabase", "✅ Migration 9 -> 10: Added driver online/offline status")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create passenger_vehicles table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS passenger_vehicles (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        passengerId TEXT NOT NULL,
                        type TEXT NOT NULL,
                        brand TEXT NOT NULL,
                        model TEXT NOT NULL,
                        year INTEGER NOT NULL,
                        licensePlate TEXT NOT NULL,
                        transmission TEXT,
                        seats INTEGER,
                        engineCapacity TEXT,
                        imageUrl TEXT,
                        isActive INTEGER NOT NULL DEFAULT 1,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                
                android.util.Log.d("AppDatabase", "✅ Migration 10 -> 11: Created passenger_vehicles table")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create driver_requests table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS driver_requests (
                        id TEXT PRIMARY KEY NOT NULL,
                        passengerEmail TEXT NOT NULL,
                        passengerName TEXT NOT NULL,
                        driverEmail TEXT NOT NULL,
                        driverName TEXT,
                        passengerVehicleId TEXT NOT NULL,
                        vehicleBrand TEXT NOT NULL,
                        vehicleModel TEXT NOT NULL,
                        vehicleType TEXT NOT NULL,
                        vehicleLicensePlate TEXT NOT NULL,
                        pickupAddress TEXT NOT NULL,
                        pickupLat REAL NOT NULL,
                        pickupLon REAL NOT NULL,
                        destinationAddress TEXT,
                        destinationLat REAL,
                        destinationLon REAL,
                        status TEXT NOT NULL,
                        driverArrivalMethod TEXT,
                        estimatedArrivalMinutes INTEGER,
                        driverCurrentLat REAL,
                        driverCurrentLon REAL,
                        acceptedAt INTEGER,
                        startedAt INTEGER,
                        arrivedAt INTEGER,
                        completedAt INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        synced INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                
                // Create indexes
                database.execSQL("CREATE INDEX IF NOT EXISTS index_driver_requests_driverEmail ON driver_requests(driverEmail)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_driver_requests_passengerEmail ON driver_requests(passengerEmail)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_driver_requests_status ON driver_requests(status)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_driver_requests_createdAt ON driver_requests(createdAt)")
                
                android.util.Log.d("AppDatabase", "✅ Migration 11 -> 12: Created driver_requests table")
            }
        }
        
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add new fields to rentals table for delivery mode
                database.execSQL("ALTER TABLE rentals ADD COLUMN deliveryMode TEXT")
                database.execSQL("ALTER TABLE rentals ADD COLUMN ownerEmail TEXT")
                database.execSQL("ALTER TABLE rentals ADD COLUMN deliveryDriverId TEXT")
                database.execSQL("ALTER TABLE rentals ADD COLUMN deliveryStatus TEXT")
                database.execSQL("ALTER TABLE rentals ADD COLUMN travelDriverId TEXT")
                database.execSQL("ALTER TABLE rentals ADD COLUMN deliveryStartedAt INTEGER")
                database.execSQL("ALTER TABLE rentals ADD COLUMN deliveryArrivedAt INTEGER")
                database.execSQL("ALTER TABLE rentals ADD COLUMN travelStartedAt INTEGER")
                
                // Create chat_channels table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS chat_channels (
                        id TEXT PRIMARY KEY NOT NULL,
                        channelType TEXT NOT NULL,
                        participant1 TEXT NOT NULL,
                        participant2 TEXT NOT NULL,
                        participant3 TEXT,
                        rentalId TEXT,
                        lastMessageAt INTEGER NOT NULL,
                        lastMessage TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                
                // Create chat_messages table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS chat_messages (
                        id TEXT PRIMARY KEY NOT NULL,
                        channelId TEXT NOT NULL,
                        senderEmail TEXT NOT NULL,
                        senderName TEXT NOT NULL,
                        message TEXT NOT NULL,
                        messageType TEXT NOT NULL DEFAULT 'TEXT',
                        isRead INTEGER NOT NULL DEFAULT 0,
                        readAt INTEGER,
                        createdAt INTEGER NOT NULL,
                        synced INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                
                // Create indexes for chat
                database.execSQL("CREATE INDEX IF NOT EXISTS index_chat_channels_participant1 ON chat_channels(participant1)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_chat_channels_participant2 ON chat_channels(participant2)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_chat_channels_participant3 ON chat_channels(participant3)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_chat_channels_rentalId ON chat_channels(rentalId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_chat_channels_type ON chat_channels(channelType)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_chat_channels_lastMessageAt ON chat_channels(lastMessageAt)")
                
                database.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_channelId ON chat_messages(channelId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_senderEmail ON chat_messages(senderEmail)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_createdAt ON chat_messages(createdAt)")
                
                android.util.Log.d("AppDatabase", "✅ Migration 12 -> 13: Added delivery mode fields and chat tables")
            }
        }
        
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create payment_history table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS payment_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userId INTEGER NOT NULL,
                        userEmail TEXT NOT NULL,
                        rentalId TEXT NOT NULL,
                        vehicleName TEXT NOT NULL,
                        amount INTEGER NOT NULL,
                        paymentMethod TEXT NOT NULL,
                        paymentType TEXT NOT NULL,
                        ownerEmail TEXT NOT NULL,
                        driverEmail TEXT,
                        ownerIncome INTEGER NOT NULL,
                        driverIncome INTEGER NOT NULL DEFAULT 0,
                        status TEXT NOT NULL DEFAULT 'COMPLETED',
                        createdAt INTEGER NOT NULL,
                        synced INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                
                // Create income_history table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS income_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        recipientEmail TEXT NOT NULL,
                        recipientRole TEXT NOT NULL,
                        rentalId TEXT NOT NULL,
                        paymentHistoryId INTEGER NOT NULL,
                        vehicleName TEXT NOT NULL,
                        passengerEmail TEXT NOT NULL,
                        amount INTEGER NOT NULL,
                        paymentMethod TEXT NOT NULL,
                        paymentType TEXT NOT NULL,
                        status TEXT NOT NULL DEFAULT 'COMPLETED',
                        createdAt INTEGER NOT NULL,
                        synced INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                
                // Create indexes
                database.execSQL("CREATE INDEX IF NOT EXISTS index_payment_history_userId ON payment_history(userId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_payment_history_userEmail ON payment_history(userEmail)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_payment_history_rentalId ON payment_history(rentalId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_payment_history_createdAt ON payment_history(createdAt)")
                
                database.execSQL("CREATE INDEX IF NOT EXISTS index_income_history_recipientEmail ON income_history(recipientEmail)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_income_history_recipientRole ON income_history(recipientRole)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_income_history_rentalId ON income_history(rentalId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_income_history_createdAt ON income_history(createdAt)")
                
                android.util.Log.d("AppDatabase", "✅ Migration 13 -> 14: Created payment_history and income_history tables")
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add early return fields to rentals table
                database.execSQL("ALTER TABLE rentals ADD COLUMN returnLocationLat REAL")
                database.execSQL("ALTER TABLE rentals ADD COLUMN returnLocationLon REAL")
                database.execSQL("ALTER TABLE rentals ADD COLUMN returnAddress TEXT")
                database.execSQL("ALTER TABLE rentals ADD COLUMN earlyReturnRequested INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE rentals ADD COLUMN earlyReturnStatus TEXT")
                database.execSQL("ALTER TABLE rentals ADD COLUMN earlyReturnRequestedAt INTEGER")
                
                android.util.Log.d("AppDatabase", "✅ Migration 14 -> 15: Added early return fields to rentals table")
            }
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(database: SupportSQLiteDatabase) {
                android.util.Log.d("AppDatabase", "🔄 Starting migration 15 -> 16: Creating driver_profiles table")
                
                // Step 1: Create driver_profiles table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS driver_profiles (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        driverEmail TEXT NOT NULL UNIQUE,
                        simCertifications TEXT,
                        isOnline INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        synced INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                
                // Step 2: Create indices
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_driver_profiles_driverEmail ON driver_profiles(driverEmail)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_driver_profiles_isOnline ON driver_profiles(isOnline)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_driver_profiles_synced ON driver_profiles(synced)")
                
                android.util.Log.d("AppDatabase", "✅ Created driver_profiles table")
                
                // Step 3: Migrate existing data from users table to driver_profiles
                // Only migrate users with role containing "DRIVER" or "driver"
                try {
                    val now = System.currentTimeMillis()
                    database.execSQL("""
                        INSERT INTO driver_profiles (driverEmail, simCertifications, isOnline, createdAt, updatedAt, synced)
                        SELECT 
                            email,
                            simCertifications,
                            COALESCE(isOnline, 0),
                            createdAt,
                            $now,
                            0
                        FROM users
                        WHERE (UPPER(role) LIKE '%DRIVER%' OR UPPER(role) = 'DRIVER')
                        AND NOT EXISTS (
                            SELECT 1 FROM driver_profiles WHERE driver_profiles.driverEmail = users.email
                        )
                    """.trimIndent())
                    
                    android.util.Log.d("AppDatabase", "✅ Migrated existing driver data to driver_profiles")
                } catch (e: Exception) {
                    android.util.Log.e("AppDatabase", "⚠️ Error migrating driver data: ${e.message}", e)
                    // Continue migration even if data migration fails
                }
                
                // Step 4: Remove simCertifications and isOnline columns from users table
                // SQLite doesn't support DROP COLUMN directly, so we need to recreate the table
                try {
                    // Create new users table without simCertifications and isOnline
                    database.execSQL("""
                        CREATE TABLE users_new (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            email TEXT NOT NULL UNIQUE,
                            password TEXT NOT NULL,
                            role TEXT NOT NULL,
                            fullName TEXT,
                            phoneNumber TEXT,
                            createdAt INTEGER NOT NULL,
                            synced INTEGER NOT NULL DEFAULT 0
                        )
                    """.trimIndent())
                    
                    // Copy data from old table (excluding simCertifications and isOnline)
                    database.execSQL("""
                        INSERT INTO users_new (id, email, password, role, fullName, phoneNumber, createdAt, synced)
                        SELECT id, email, password, role, fullName, phoneNumber, createdAt, synced
                        FROM users
                    """.trimIndent())
                    
                    // Drop old table
                    database.execSQL("DROP TABLE users")
                    
                    // Rename new table
                    database.execSQL("ALTER TABLE users_new RENAME TO users")
                    
                    // Recreate unique index on email
                    database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_users_email ON users(email)")
                    
                    android.util.Log.d("AppDatabase", "✅ Removed simCertifications and isOnline from users table")
                } catch (e: Exception) {
                    android.util.Log.e("AppDatabase", "❌ Error removing columns from users table: ${e.message}", e)
                    // If this fails, the app will still work but with duplicate columns
                }
                
                android.util.Log.d("AppDatabase", "✅ Migration 15 -> 16 completed successfully")
            }
        }

        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(database: SupportSQLiteDatabase) {
                android.util.Log.d("AppDatabase", "🔄 Starting migration 16 -> 17: Creating user_balances and balance_transactions tables")
                
                // Step 1: Create user_balances table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS user_balances (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userId INTEGER NOT NULL,
                        userEmail TEXT NOT NULL UNIQUE,
                        balance INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        synced INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                
                // Step 2: Create balance_transactions table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS balance_transactions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userId INTEGER NOT NULL,
                        userEmail TEXT NOT NULL,
                        relatedUserId INTEGER,
                        relatedUserEmail TEXT,
                        transactionType TEXT NOT NULL,
                        source TEXT NOT NULL,
                        serviceType TEXT,
                        amount INTEGER NOT NULL,
                        balanceBefore INTEGER NOT NULL,
                        balanceAfter INTEGER NOT NULL,
                        rentalId TEXT,
                        vehicleId INTEGER,
                        description TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        synced INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                
                // Step 3: Create indices for user_balances
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_user_balances_userEmail ON user_balances(userEmail)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_user_balances_synced ON user_balances(synced)")
                
                // Step 4: Create indices for balance_transactions
                database.execSQL("CREATE INDEX IF NOT EXISTS index_balance_transactions_userEmail ON balance_transactions(userEmail)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_balance_transactions_relatedUserEmail ON balance_transactions(relatedUserEmail)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_balance_transactions_transactionType ON balance_transactions(transactionType)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_balance_transactions_source ON balance_transactions(source)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_balance_transactions_createdAt ON balance_transactions(createdAt)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_balance_transactions_synced ON balance_transactions(synced)")
                
                // Step 5: Initialize balances for existing users with Rp 4,500,000
                try {
                    val initialBalance = 4_500_000L
                    val now = System.currentTimeMillis()
                    database.execSQL("""
                        INSERT INTO user_balances (userId, userEmail, balance, createdAt, updatedAt, synced)
                        SELECT 
                            id,
                            email,
                            $initialBalance,
                            $now,
                            $now,
                            0
                        FROM users
                        WHERE NOT EXISTS (
                            SELECT 1 FROM user_balances WHERE user_balances.userEmail = users.email
                        )
                    """.trimIndent())
                    
                    android.util.Log.d("AppDatabase", "✅ Initialized balances for existing users")
                } catch (e: Exception) {
                    android.util.Log.e("AppDatabase", "⚠️ Error initializing balances: ${e.message}", e)
                }
                
                android.util.Log.d("AppDatabase", "✅ Migration 16 -> 17 completed successfully")
            }
        }

        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add synced field to passenger_vehicles table
                database.execSQL("ALTER TABLE passenger_vehicles ADD COLUMN synced INTEGER NOT NULL DEFAULT 0")
                
                android.util.Log.d("AppDatabase", "✅ Migration 17 -> 18: Added synced field to passenger_vehicles table")
            }
        }

        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add driverAssignmentMode field to vehicles table
                database.execSQL("ALTER TABLE vehicles ADD COLUMN driverAssignmentMode TEXT")
                
                android.util.Log.d("AppDatabase", "✅ Migration 18 -> 19: Added driverAssignmentMode field to vehicles table")
            }
        }

        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add balanceSynced field to income_history table
                // This prevents income from being processed to balance multiple times
                database.execSQL("ALTER TABLE income_history ADD COLUMN balanceSynced INTEGER NOT NULL DEFAULT 0")
                
                android.util.Log.d("AppDatabase", "✅ Migration 19 -> 20: Added balanceSynced field to income_history table")
            }
        }

        private val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create driver_rentals table for independent driver rentals
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS driver_rentals (
                        id TEXT NOT NULL PRIMARY KEY,
                        passengerEmail TEXT NOT NULL,
                        passengerName TEXT,
                        driverEmail TEXT NOT NULL,
                        driverName TEXT,
                        vehicleType TEXT NOT NULL,
                        durationType TEXT NOT NULL,
                        durationCount INTEGER NOT NULL,
                        price INTEGER NOT NULL,
                        paymentMethod TEXT NOT NULL,
                        pickupAddress TEXT NOT NULL,
                        pickupLat REAL NOT NULL,
                        pickupLon REAL NOT NULL,
                        destinationAddress TEXT,
                        destinationLat REAL,
                        destinationLon REAL,
                        status TEXT NOT NULL,
                        startDate INTEGER,
                        endDate INTEGER,
                        confirmedAt INTEGER,
                        completedAt INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        synced INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                
                // Create indices
                database.execSQL("CREATE INDEX IF NOT EXISTS index_driver_rentals_driverEmail ON driver_rentals(driverEmail)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_driver_rentals_passengerEmail ON driver_rentals(passengerEmail)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_driver_rentals_status ON driver_rentals(status)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_driver_rentals_createdAt ON driver_rentals(createdAt)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_driver_rentals_synced ON driver_rentals(synced)")
                
                android.util.Log.d("AppDatabase", "✅ Migration 20 -> 21: Created driver_rentals table")
            }
        }

        private val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add senderRole and receiverRole to payment_history table
                database.execSQL("ALTER TABLE payment_history ADD COLUMN senderRole TEXT")
                database.execSQL("ALTER TABLE payment_history ADD COLUMN receiverRole TEXT")
                
                android.util.Log.d("AppDatabase", "✅ Migration 21 -> 22: Added senderRole and receiverRole to payment_history")
            }
        }

        private val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add payment breakdown fields to rentals table (for Sewa Kendaraan + Driver)
                database.execSQL("ALTER TABLE rentals ADD COLUMN vehicleRentalAmount INTEGER")
                database.execSQL("ALTER TABLE rentals ADD COLUMN driverAmount INTEGER")
                
                android.util.Log.d("AppDatabase", "✅ Migration 22 -> 23: Added vehicleRentalAmount and driverAmount to rentals table")
            }
        }

        private val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add username column to users table
                database.execSQL("ALTER TABLE users ADD COLUMN username TEXT")
                
                // Create unique index on username
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_users_username ON users(username)")
                
                // Populate username from email (substring before '@')
                database.execSQL("""
                    UPDATE users 
                    SET username = substr(email, 1, instr(email, '@') - 1)
                    WHERE username IS NULL OR username = ''
                """.trimIndent())
                
                android.util.Log.d("AppDatabase", "✅ Migration 23 -> 24: Added username column to users table")
            }
        }
        
        private val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Remove passengerName column from driver_rentals table
                // SQLite doesn't support DROP COLUMN directly, so we need to recreate the table
                database.execSQL("""
                    CREATE TABLE driver_rentals_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        passengerEmail TEXT NOT NULL,
                        driverEmail TEXT NOT NULL,
                        driverName TEXT,
                        vehicleType TEXT NOT NULL,
                        durationType TEXT NOT NULL,
                        durationCount INTEGER NOT NULL,
                        price INTEGER NOT NULL,
                        paymentMethod TEXT NOT NULL,
                        pickupAddress TEXT NOT NULL,
                        pickupLat REAL NOT NULL,
                        pickupLon REAL NOT NULL,
                        destinationAddress TEXT,
                        destinationLat REAL,
                        destinationLon REAL,
                        status TEXT NOT NULL,
                        startDate INTEGER,
                        endDate INTEGER,
                        confirmedAt INTEGER,
                        completedAt INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        synced INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                
                // Copy data from old table (excluding passengerName)
                database.execSQL("""
                    INSERT INTO driver_rentals_new (
                        id, passengerEmail, driverEmail, driverName, vehicleType, durationType, durationCount,
                        price, paymentMethod, pickupAddress, pickupLat, pickupLon, destinationAddress,
                        destinationLat, destinationLon, status, startDate, endDate, confirmedAt, completedAt,
                        createdAt, updatedAt, synced
                    )
                    SELECT 
                        id, passengerEmail, driverEmail, driverName, vehicleType, durationType, durationCount,
                        price, paymentMethod, pickupAddress, pickupLat, pickupLon, destinationAddress,
                        destinationLat, destinationLon, status, startDate, endDate, confirmedAt, completedAt,
                        createdAt, updatedAt, synced
                    FROM driver_rentals
                """.trimIndent())
                
                // Drop old table
                database.execSQL("DROP TABLE driver_rentals")
                
                // Rename new table
                database.execSQL("ALTER TABLE driver_rentals_new RENAME TO driver_rentals")
                
                // Recreate indices
                database.execSQL("CREATE INDEX IF NOT EXISTS index_driver_rentals_driverEmail ON driver_rentals (driverEmail)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_driver_rentals_passengerEmail ON driver_rentals (passengerEmail)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_driver_rentals_status ON driver_rentals (status)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_driver_rentals_createdAt ON driver_rentals (createdAt)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_driver_rentals_synced ON driver_rentals (synced)")
                
                android.util.Log.d("AppDatabase", "✅ Migration 24 -> 25: Removed passengerName column from driver_rentals table")
            }
        }
        
        private val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(database: SupportSQLiteDatabase) {
                android.util.Log.d("AppDatabase", "🔄 Starting migration 25 -> 26: Chat system order-based updates")
                
                // Step 1: Add orderStatus column (nullable first)
                try {
                    database.execSQL("ALTER TABLE chat_channels ADD COLUMN orderStatus TEXT")
                    android.util.Log.d("AppDatabase", "✅ Added orderStatus column to chat_channels")
                } catch (e: Exception) {
                    android.util.Log.e("AppDatabase", "❌ Error adding orderStatus: ${e.message}", e)
                }
                
                // Step 2: Update existing channels with orderStatus based on rental status
                // For channels with rentalId, get status from rentals table
                // For channels without rentalId, mark as "COMPLETED" (legacy data)
                try {
                    // First, set default status for channels without rentalId
                    database.execSQL("""
                        UPDATE chat_channels 
                        SET orderStatus = 'COMPLETED' 
                        WHERE rentalId IS NULL OR rentalId = ''
                    """.trimIndent())
                    
                    // Then, update channels with rentalId by joining with rentals table
                    // Note: SQLite doesn't support UPDATE with JOIN directly, so we use a subquery
                    database.execSQL("""
                        UPDATE chat_channels 
                        SET orderStatus = COALESCE(
                            (SELECT status FROM rentals WHERE rentals.id = chat_channels.rentalId),
                            'COMPLETED'
                        )
                        WHERE rentalId IS NOT NULL AND rentalId != ''
                    """.trimIndent())
                    
                    // Also check driver_rentals for driver-only rentals
                    database.execSQL("""
                        UPDATE chat_channels 
                        SET orderStatus = COALESCE(
                            (SELECT status FROM driver_rentals WHERE driver_rentals.id = chat_channels.rentalId),
                            orderStatus
                        )
                        WHERE rentalId IS NOT NULL AND rentalId != '' 
                        AND orderStatus = 'COMPLETED'
                    """.trimIndent())
                    
                    android.util.Log.d("AppDatabase", "✅ Updated orderStatus for existing channels")
                } catch (e: Exception) {
                    android.util.Log.e("AppDatabase", "❌ Error updating orderStatus: ${e.message}", e)
                }
                
                // Step 3: Make rentalId NOT NULL by recreating table
                // First, create new table with rentalId NOT NULL
                try {
                    database.execSQL("""
                        CREATE TABLE chat_channels_new (
                            id TEXT NOT NULL PRIMARY KEY,
                            channelType TEXT NOT NULL,
                            participant1 TEXT NOT NULL,
                            participant2 TEXT NOT NULL,
                            participant3 TEXT,
                            rentalId TEXT NOT NULL,
                            orderStatus TEXT NOT NULL,
                            lastMessageAt INTEGER NOT NULL,
                            lastMessage TEXT,
                            createdAt INTEGER NOT NULL,
                            updatedAt INTEGER NOT NULL
                        )
                    """.trimIndent())
                    
                    // Copy data from old table, using rentalId or generating one for legacy data
                    database.execSQL("""
                        INSERT INTO chat_channels_new (
                            id, channelType, participant1, participant2, participant3,
                            rentalId, orderStatus, lastMessageAt, lastMessage, createdAt, updatedAt
                        )
                        SELECT 
                            id, channelType, participant1, participant2, participant3,
                            COALESCE(NULLIF(rentalId, ''), 'LEGACY_' || id) as rentalId,
                            COALESCE(NULLIF(orderStatus, ''), 'COMPLETED') as orderStatus,
                            lastMessageAt, lastMessage, createdAt, updatedAt
                        FROM chat_channels
                    """.trimIndent())
                    
                    // Drop old table
                    database.execSQL("DROP TABLE chat_channels")
                    
                    // Rename new table
                    database.execSQL("ALTER TABLE chat_channels_new RENAME TO chat_channels")
                    
                    // Recreate indices
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_chat_channels_participant1 ON chat_channels(participant1)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_chat_channels_participant2 ON chat_channels(participant2)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_chat_channels_participant3 ON chat_channels(participant3)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_chat_channels_rentalId ON chat_channels(rentalId)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_chat_channels_channelType ON chat_channels(channelType)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_chat_channels_orderStatus ON chat_channels(orderStatus)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_chat_channels_lastMessageAt ON chat_channels(lastMessageAt)")
                    
                    android.util.Log.d("AppDatabase", "✅ Recreated chat_channels table with required rentalId and orderStatus")
                } catch (e: Exception) {
                    android.util.Log.e("AppDatabase", "❌ Error recreating chat_channels table: ${e.message}", e)
                }
                
                android.util.Log.d("AppDatabase", "✅ Migration 25 -> 26 completed: Chat system order-based updates")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                try {
                    val instance = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "jalanin_database"
                    )
                        .addMigrations(
                            MIGRATION_1_2,
                            MIGRATION_2_3,
                            MIGRATION_3_4,
                            MIGRATION_4_5,
                            MIGRATION_5_6,
                            MIGRATION_6_7,
                            MIGRATION_7_8,
                            MIGRATION_8_9,
                            MIGRATION_9_10,
                            MIGRATION_10_11,
                            MIGRATION_11_12,
                            MIGRATION_12_13,
                            MIGRATION_13_14,
                            MIGRATION_14_15,
                            MIGRATION_15_16,
                            MIGRATION_16_17,
                            MIGRATION_17_18,
                            MIGRATION_18_19,
                            MIGRATION_19_20,
                            MIGRATION_20_21,
                            MIGRATION_21_22,
                            MIGRATION_22_23,
                            MIGRATION_23_24,
                            MIGRATION_24_25,
                            MIGRATION_25_26
                        )
                        .fallbackToDestructiveMigration() // ✅ Allow database recreation for development
                        .fallbackToDestructiveMigrationOnDowngrade()
                        .build()
                    INSTANCE = instance
                    android.util.Log.d("AppDatabase", "✅ Database instance created successfully")
                    instance
                } catch (e: IllegalStateException) {
                    // ✅ CRITICAL: Handle schema mismatch (hash verification failed)
                    // Room checks hash BEFORE migration, so we need to delete and recreate
                    if (e.message?.contains("Room cannot verify the data integrity") == true || 
                        e.message?.contains("identity hash") == true ||
                        e.message?.contains("forgot to update the version number") == true) {
                        android.util.Log.w("AppDatabase", "⚠️ Schema mismatch detected, deleting old database...")
                        try {
                            // Close existing instance if any
                            INSTANCE?.close()
                            INSTANCE = null
                            
                            // Delete the corrupted database
                            context.applicationContext.deleteDatabase("jalanin_database")
                            android.util.Log.d("AppDatabase", "🗑️ Old database deleted, recreating...")
                            
                            // Recreate database
                            val instance = Room.databaseBuilder(
                                context.applicationContext,
                                AppDatabase::class.java,
                                "jalanin_database"
                            )
                                .addMigrations(
                                    MIGRATION_1_2,
                                    MIGRATION_2_3,
                                    MIGRATION_3_4,
                                    MIGRATION_4_5,
                                    MIGRATION_5_6,
                                    MIGRATION_6_7,
                                    MIGRATION_7_8,
                                    MIGRATION_8_9,
                                    MIGRATION_9_10,
                                    MIGRATION_10_11,
                                    MIGRATION_11_12,
                                    MIGRATION_12_13
                                )
                                .fallbackToDestructiveMigration()
                                .fallbackToDestructiveMigrationOnDowngrade()
                                .build()
                            INSTANCE = instance
                            android.util.Log.d("AppDatabase", "✅ Database recreated successfully after schema mismatch")
                            instance
                        } catch (e2: Exception) {
                            android.util.Log.e("AppDatabase", "❌ Fatal error: Cannot recreate database: ${e2.message}", e2)
                            throw e2 // Re-throw if we can't recover
                        }
                    } else {
                        // Other IllegalStateException, re-throw
                        throw e
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AppDatabase", "❌ Critical error creating database: ${e.message}", e)
                    // Try to delete corrupted database and recreate
                    try {
                        INSTANCE?.close()
                        INSTANCE = null
                        context.applicationContext.deleteDatabase("jalanin_database")
                        android.util.Log.d("AppDatabase", "🗑️ Deleted corrupted database, recreating...")
                        val instance = Room.databaseBuilder(
                            context.applicationContext,
                            AppDatabase::class.java,
                            "jalanin_database"
                        )
                            .addMigrations(
                                MIGRATION_1_2,
                                MIGRATION_2_3,
                                MIGRATION_3_4,
                                MIGRATION_4_5,
                                MIGRATION_5_6,
                                MIGRATION_6_7,
                                MIGRATION_7_8,
                                MIGRATION_8_9
                            )
                            .fallbackToDestructiveMigration()
                            .fallbackToDestructiveMigrationOnDowngrade()
                            .build()
                        INSTANCE = instance
                        android.util.Log.d("AppDatabase", "✅ Database recreated successfully")
                        instance
                    } catch (e2: Exception) {
                        android.util.Log.e("AppDatabase", "❌ Fatal error: Cannot create database: ${e2.message}", e2)
                        throw e2 // Re-throw if we can't recover
                    }
                }
            }
        }

        /**
         * ✅ Clear database instance (for testing or after logout)
         */
        fun clearInstance() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
                android.util.Log.d("AppDatabase", "Database instance cleared")
            }
        }
    }
}
