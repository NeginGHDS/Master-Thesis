package com.example.offline_first

import com.example.offline_first.data.local.AppDatabase
import com.example.offline_first.data.local.SessionStore
import com.example.offline_first.sync.ConnectivityObserver
import com.example.offline_first.sync.StorageManager
import com.example.offline_first.sync.SyncManager
import com.example.offline_first.sync.createConnectivityObserver
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.auth

/**
 * Single shared place that wires up everything the original app built ad hoc via
 * `by lazy { AppDatabase.getInstance(this) }` in each Activity: the DB/DAOs, [SyncManager],
 * [ConnectivityObserver] and [SessionStore]. Built once per process from the platform-supplied
 * [AppDatabase] (see `createDatabaseBuilder` in androidMain/iosMain) and handed down to the
 * ViewModels, so every screen shares the exact same instances — in particular the same
 * [SyncManager]/[ConnectivityObserver] pair that drives auto-sync.
 */
class AppContainer(database: AppDatabase) {
    val reportDao = database.reportDao()
    val userDao = database.userDao()

    val auth: FirebaseAuth = Firebase.auth
    val storageManager = StorageManager()
    val syncManager = SyncManager(reportDao, storageManager)
    val connectivityObserver: ConnectivityObserver = createConnectivityObserver()
    val sessionStore = SessionStore()
}
