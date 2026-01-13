package com.example.app_jalanin.ui.register

/** Data form dasar untuk pendaftaran driver. */
data class DriverRegistrationData(
    val driverTypeId: Int,
    val fullName: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val idCardNumber: String = "",
    // Field umum tambahan
    val photoSelfPath: String = "", // path / uri foto diri
    // Kendaraan
    val vehicleCategory: String = "",     // scooter/bebek/moge atau sedan/mpv/suv
    val vehicleEngineCc: String = "",     // wajib diisi jika kategori moge
    val vehicleBrandModel: String = "", // contoh: "Honda Vario 150"
    // Field spesifik per tipe
    val motorPlate: String = "",      // motor & mobil gunakan field ini sebagai nomor polisi
    val carPlate: String = "",        // tetap untuk kompatibilitas lama
    // Driver Pengganti khusus
    val simType: String = "",          // SIM A/B/C
    val experienceYears: String = "",   // lama pengalaman
    val shiftAvailability: String = "", // pengganti
    val fleetSize: String = "",        // pemilik
    val vehiclePhotoPath: String = "",    // foto kendaraan
    // Dokumen unggahan
    val simDocumentPath: String = "",  // SIM
    val stnkDocumentPath: String = "",  // STNK
    // Pemilik Kendaraan Sewa
    val locationAddress: String = "",      // alamat lokasi kendaraan (pemilik)
    val ownerVehicleType: String = "",     // Motor / Mobil (pemilik kendaraan)
    val vehicleYear: String = "",          // Tahun kendaraan (pemilik)
    val vehicleCapacity: String = "",      // cc atau jumlah kursi (pemilik)
    val rentalPrice: String = ""           // harga sewa (format bebas: hari/minggu/jam)
)
