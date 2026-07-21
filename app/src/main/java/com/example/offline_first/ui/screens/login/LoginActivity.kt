package com.example.offline_first.ui.screens.login

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.example.offline_first.MainActivity
import com.example.offline_first.R
import com.example.offline_first.data.local.AppDatabase
import com.example.offline_first.data.model.User
import com.example.offline_first.databinding.ActivityLoginBinding
import com.example.offline_first.ui.screens.signup.SignUpActivity
import com.example.offline_first.util.CryptoUtils
import com.example.offline_first.util.NetworkUtils
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val auth = FirebaseAuth.getInstance()
    private val userDao by lazy { AppDatabase.getInstance(this).userDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityLoginBinding.inflate(layoutInflater)
        // setContentView just says "this is what the screen should show".
        setContentView(binding.root)
        // your layout expands to fill the entire screen including behind the status bar and navigation bar. This block then adds padding equal to
        //  the size of those bars so your content stays inside the visible area.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.buttonLogin.setOnClickListener { signInWithEmail() }
        binding.buttonGoogle.setOnClickListener { signInWithGoogle() }
        binding.buttonTwitter.setOnClickListener {
            Toast.makeText(this, "Twitter Login", Toast.LENGTH_SHORT).show()
        }
        binding.textSignUp.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }
    }

    private fun emailAndPassword(): Pair<String, String>? {
        val email = binding.inputUsername.editText?.text?.toString().orEmpty().trim()
        val password = binding.inputPassword.editText?.text?.toString().orEmpty()

        if (email.isEmpty()) {
            binding.inputUsername.error = "Email is required"
            return null
        }
        binding.inputUsername.error = null

        if (password.isEmpty()) {
            binding.inputPassword.error = "Password is required"
            return null
        }
        binding.inputPassword.error = null

        return email to password
    }

    private fun signInWithEmail() {
        val (email, password) = emailAndPassword() ?: return
        setLoading(true)

        if (!NetworkUtils.isOnline(this)) {
            lifecycleScope.launch {
                val ok = withContext(Dispatchers.IO) { verifyLocalCredential(email, password) }
                setLoading(false)
                if (ok) {
                    navigateToHome(localEmail = email)
                } else {
                    Toast.makeText(
                        this@LoginActivity,
                        "Incorrect email or password.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            return
        }

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        saveLocalCredential(email, password, result.user?.displayName)
                    }
                    navigateToHome()
                }
            }
            .addOnFailureListener {
                Log.e(TAG, "signInWithEmail: failed", it)
                Toast.makeText(this, "Sign-in failed: ${it.message}", Toast.LENGTH_LONG).show()
            }
            .addOnCompleteListener { setLoading(false) }
    }

    private suspend fun verifyLocalCredential(email: String, password: String): Boolean {
        val user = userDao.findByEmail(email) ?: return false
        return CryptoUtils.verify(password, user.salt, user.passwordHash)
    }

    suspend fun saveLocalCredential(email: String, password: String, displayName: String?) {
        val salt = CryptoUtils.generateSalt()
        val hash = CryptoUtils.hashPassword(password, salt)
        userDao.upsert(User(email = email, passwordHash = hash, salt = salt, displayName = displayName))
    }

    private fun setLoading(loading: Boolean) {
        binding.buttonLogin.isEnabled = !loading
        binding.buttonGoogle.isEnabled = !loading
        binding.textSignUp.isEnabled = !loading
    }

    private fun signInWithGoogle() {
        val webClientId = getString(R.string.web_client_id)
        Log.d(TAG, "signInWithGoogle: starting. webClientId=$webClientId")

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
                Log.d(TAG, "signInWithGoogle: calling credentialManager.getCredential")
                val result = credentialManager.getCredential(this@LoginActivity, request)
                Log.d(TAG, "signInWithGoogle: credential received, type=${result.credential.type}")

                val credential = GoogleIdTokenCredential.createFrom(result.credential.data)
                Log.d(TAG, "signInWithGoogle: idToken=${credential.idToken.take(20)}...")

                val firebaseCredential = GoogleAuthProvider.getCredential(credential.idToken, null)
                auth.signInWithCredential(firebaseCredential)
                    .addOnSuccessListener {
                        Log.d(TAG, "signInWithGoogle: Firebase auth success, user=${it.user?.email}")
                        navigateToHome()
                    }
                    .addOnFailureListener {
                        Log.e(TAG, "signInWithGoogle: Firebase auth failed", it)
                        Toast.makeText(this@LoginActivity, "Auth failed: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            } catch (e: GetCredentialException) {
                Log.e(TAG, "signInWithGoogle: GetCredentialException type=${e::class.simpleName} msg=${e.message}", e)
                Toast.makeText(this@LoginActivity, "Sign-in failed: ${e.message}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e(TAG, "signInWithGoogle: unexpected error", e)
                Toast.makeText(this@LoginActivity, "Unexpected error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun navigateToHome(localEmail: String? = null) {
        getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE).edit().apply {
            putLong(MainActivity.KEY_LOGIN_TIME, System.currentTimeMillis())
            if (localEmail != null) putString(MainActivity.KEY_LOCAL_AUTH_EMAIL, localEmail)
            else remove(MainActivity.KEY_LOCAL_AUTH_EMAIL)
        }.apply()
        startActivity(Intent(this, MainActivity::class.java).apply {
            //start MainActivity as a fresh task or wipe everything else off the back stack (including LoginActivity itself)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }

    companion object {
        private const val TAG = "LoginActivity"
    }
}