package com.example.offline_first.util

actual fun readFileBytes(path: String): ByteArray? =
    try { java.io.File(path).readBytes() } catch (_: Exception) { null }
