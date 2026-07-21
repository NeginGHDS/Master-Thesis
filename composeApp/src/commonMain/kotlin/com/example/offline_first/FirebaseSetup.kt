package com.example.offline_first

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.firestoreSettings
import dev.gitlive.firebase.firestore.memoryCacheSettings

/**
 * Configures Firestore identically to the original `OfflineFirstApp.onCreate`
 * (`FirebaseFirestoreSettings.Builder().setPersistenceEnabled(false).build()`): Room is this
 * app's offline source of truth, so Firestore's own on-disk cache is replaced with an in-memory
 * cache to avoid two offline stores disagreeing with each other (the modern GitLive/Firestore API
 * expresses "no on-disk persistence" as `cacheSettings = memoryCacheSettings { }` rather than the
 * old `persistenceEnabled` flag). Call once, after `Firebase.initialize(context)` on each platform
 * (Android: from `OfflineFirstApp.onCreate`; iOS: from `MainViewController`'s lazy Firebase init).
 */
fun configureFirestore() {
    Firebase.firestore.settings = firestoreSettings {
        cacheSettings = memoryCacheSettings { }
    }
}
