package com.example.app_jalanin.data.auth

import androidx.room.TypeConverter

class RoleConverters {
    @TypeConverter
    fun toString(role: UserRole): String = role.name

    @TypeConverter
    fun toRole(value: String): UserRole = UserRole.valueOf(value)
}

