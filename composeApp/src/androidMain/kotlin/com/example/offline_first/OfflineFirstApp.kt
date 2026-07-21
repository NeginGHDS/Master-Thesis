package com.example.offline_first

import android.app.Application
import com.google.firebase.FirebaseApp

class OfflineFirstApp : Application() {

    override fun onCreate() {
        super.onCreate()
        initAndroidAppContext(this)
        FirebaseApp.initializeApp(this)
        configureFirestore()
    }
}
