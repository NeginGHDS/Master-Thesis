package com.example.offline_first.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
