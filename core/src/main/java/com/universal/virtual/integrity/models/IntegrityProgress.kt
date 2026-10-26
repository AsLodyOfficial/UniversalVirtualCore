package com.universal.virtual.integrity.models

data class IntegrityProgress(
    val completed: Int,
    val total: Int,
    val currentCheck: String,
    val percentage: Float
)
