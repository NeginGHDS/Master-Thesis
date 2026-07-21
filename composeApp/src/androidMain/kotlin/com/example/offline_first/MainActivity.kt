package com.example.offline_first

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.example.offline_first.data.local.buildAppDatabase
import com.example.offline_first.data.local.createDatabaseBuilder
import com.example.offline_first.ui.App
import com.example.offline_first.ui.PlatformActions
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    private var pendingPhotoResult: ((String?) -> Unit)? = null
    private var pendingCameraFile: File? = null

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            val callback = pendingPhotoResult
            pendingPhotoResult = null
            callback?.invoke(uri?.let { copyUriToLocalFile(it) })
        }

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val callback = pendingPhotoResult
            pendingPhotoResult = null
            val file = pendingCameraFile
            pendingCameraFile = null
            if (success && file != null) {
                callback?.invoke(file.absolutePath)
            } else {
                file?.delete()
                callback?.invoke(null)
            }
        }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                launchCamera()
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
                val callback = pendingPhotoResult
                pendingPhotoResult = null
                callback?.invoke(null)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val container = remember { AppContainer(createDatabaseBuilder(applicationContext).buildAppDatabase()) }
            val platformActions = remember {
                PlatformActions(
                    pickPhotoFromGallery = { onResult ->
                        pendingPhotoResult = onResult
                        galleryLauncher.launch("image/*")
                    },
                    takePhoto = { onResult ->
                        pendingPhotoResult = onResult
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                            == PackageManager.PERMISSION_GRANTED
                        ) {
                            launchCamera()
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    signInWithGoogle = { onIdToken, onError -> signInWithGoogle(onIdToken, onError) }
                )
            }
            App(container, platformActions)
        }
    }

    private fun launchCamera() {
        val photoFile = File(
            getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            "photo_${System.currentTimeMillis()}.jpg"
        )
        pendingCameraFile = photoFile
        val uri = FileProvider.getUriForFile(this, "$packageName.provider", photoFile)
        cameraLauncher.launch(uri)
    }

    private fun copyUriToLocalFile(uri: Uri): String? = try {
        val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: filesDir
        val dest = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        contentResolver.openInputStream(uri)?.use { it.copyTo(dest.outputStream()) }
        dest.absolutePath
    } catch (e: Exception) {
        null
    }

    private fun signInWithGoogle(onIdToken: (String?) -> Unit, onError: (String?) -> Unit) {
        val webClientId = getString(R.string.web_client_id)
        val credentialManager = CredentialManager.create(this)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(this@MainActivity, request)
                val credential = GoogleIdTokenCredential.createFrom(result.credential.data)
                onIdToken(credential.idToken)
            } catch (e: GetCredentialException) {
                onError(e.message)
            } catch (e: Exception) {
                onError(e.message)
            }
        }
    }
}
