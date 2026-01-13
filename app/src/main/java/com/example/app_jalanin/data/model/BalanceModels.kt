package com.example.app_jalanin.data.model

/**
 * Enum untuk tipe layanan driver
 */
enum class DriverServiceType {
    PERSONAL_VEHICLE,  // Driver untuk kendaraan pribadi penumpang
    DELIVERY_ONLY,     // Driver untuk pengantaran kendaraan (owner -> renter)
    RENTAL_DRIVER      // Driver yang ditugaskan owner untuk rental
}

/**
 * Enum untuk tipe transaksi saldo
 */
enum class BalanceTransactionType {
    DEBIT,   // Pengurangan saldo (pembayaran)
    CREDIT   // Penambahan saldo (penerimaan)
}

/**
 * Enum untuk sumber transaksi saldo
 */
enum class BalanceTransactionSource {
    RENTER_PAYMENT,        // Pembayaran dari penumpang
    OWNER_PAYMENT,         // Pembayaran dari owner
    DRIVER_SERVICE_FEE,    // Biaya layanan driver
    DELIVERY_FEE,          // Biaya pengantaran
    RENTAL_PAYMENT,        // Pembayaran sewa kendaraan
    INITIAL_BALANCE        // Saldo awal
}

/**
 * Enum untuk tipe pricing driver (per jam, per hari, per minggu)
 */
enum class DriverPricingType {
    HOURLY,  // Per jam
    DAILY,   // Per hari
    WEEKLY   // Per minggu
}

