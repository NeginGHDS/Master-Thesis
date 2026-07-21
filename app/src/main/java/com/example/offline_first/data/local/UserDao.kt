package com.example.offline_first.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.offline_first.data.model.User

@Dao
interface UserDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(user: User)

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun findByEmail(email: String): User?
}