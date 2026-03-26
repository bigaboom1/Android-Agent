package com.example.agent

data class AuthResult(
    val jwt: String,
    val deviceId: String,
    val deviceToken: String? = null
)
