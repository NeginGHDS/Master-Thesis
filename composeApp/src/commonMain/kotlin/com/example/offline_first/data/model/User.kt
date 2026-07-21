package com.example.offline_first.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey val email: String,
    val passwordHash: String,
    val salt: String,
    val displayName: String?
)
