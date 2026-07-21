package com.example.offline_first

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.example.offline_first.data.local.buildAppDatabase
import com.example.offline_first.data.local.createDatabaseBuilder
import com.example.offline_first.ui.App
// Firebase is configured in iOSApp.init() via FirebaseApp.configure() (Swift).
// We only need to apply Firestore settings here, before the first Firestore access.
private val firestoreConfigured: Unit by lazy { configureFirestore() }

fun MainViewController() = ComposeUIViewController {
    firestoreConfigured
    val container = remember { AppContainer(createDatabaseBuilder().buildAppDatabase()) }
    val platformActions = rememberIosPlatformActions()
    App(container, platformActions)
}
