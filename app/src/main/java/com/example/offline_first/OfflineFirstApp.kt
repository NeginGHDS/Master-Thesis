package com.example.offline_first

import android.app.Application
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

class OfflineFirstApp : Application() {

    override fun onCreate() {
        super.onCreate()
        FirebaseFirestore.getInstance().firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(false)
            .build()
    }
}