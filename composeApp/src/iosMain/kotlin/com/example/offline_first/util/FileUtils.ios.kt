package com.example.offline_first.util

import platform.Foundation.NSFileManager

actual fun readFileBytes(path: String): ByteArray? =
    NSFileManager.defaultManager.contentsAtPath(path)?.toByteArray()
