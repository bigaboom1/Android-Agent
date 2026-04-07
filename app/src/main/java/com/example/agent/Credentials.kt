package com.example.agent

data class Credentials(
    val url: String,
    val jwt: String,
    val deviceId: String,
    val deviceToken: String
)
