package com.example.app_jalanin.data.auth

import java.security.MessageDigest

object PasswordHasher {
    fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString(separator = "") { b -> "%02x".format(b) }
    }
}

