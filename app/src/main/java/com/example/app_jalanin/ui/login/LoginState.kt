package com.example.app_jalanin.ui.login

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    data class Success(val email: String, val role: String, val fullName: String?) : LoginState()
    data class Error(val message: String) : LoginState()
}

