package com.example.offline_first.ui.screens.signup

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.offline_first.MainActivity
import com.example.offline_first.data.local.AppDatabase
import com.example.offline_first.data.model.User
import com.example.offline_first.databinding.ActivitySignUpBinding
import com.example.offline_first.util.CryptoUtils
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SignUpActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignUpBinding
    private val auth = FirebaseAuth.getInstance()
    private val userDao by lazy { AppDatabase.getInstance(this).userDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivitySignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.buttonSignUp.setOnClickListener { signUp() }
        binding.textAlreadyHaveAccount.setOnClickListener { finish() }
    }

    private fun signUp() {
        val email = binding.inputEmail.editText?.text?.toString().orEmpty().trim()
        val password = binding.inputPassword.editText?.text?.toString().orEmpty()
        val confirmPassword = binding.inputConfirmPassword.editText?.text?.toString().orEmpty()

        if (email.isEmpty()) {
            binding.inputEmail.error = "Email is required"
            return
        }
        binding.inputEmail.error = null

        if (password.isEmpty()) {
            binding.inputPassword.error = "Password is required"
            return
        }
        binding.inputPassword.error = null

        if (confirmPassword != password) {
            binding.inputConfirmPassword.error = "Passwords do not match"
            return
        }
        binding.inputConfirmPassword.error = null

        setLoading(true)
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        val salt = CryptoUtils.generateSalt()
                        val hash = CryptoUtils.hashPassword(password, salt)
                        userDao.upsert(
                            User(
                                email = email,
                                passwordHash = hash,
                                salt = salt,
                                displayName = result.user?.displayName
                            )
                        )
                    }
                    getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
                        .edit()
                        .putLong(MainActivity.KEY_LOGIN_TIME, System.currentTimeMillis())
                        .apply()
                    Toast.makeText(this@SignUpActivity, "Account created!", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }
            }
            .addOnFailureListener {
                Log.e(TAG, "signUp: failed", it)
                Toast.makeText(this, "Sign-up failed: ${it.message}", Toast.LENGTH_LONG).show()
            }
            .addOnCompleteListener { setLoading(false) }
    }

    private fun setLoading(loading: Boolean) {
        binding.buttonSignUp.isEnabled = !loading
        binding.textAlreadyHaveAccount.isEnabled = !loading
    }

    companion object {
        private const val TAG = "SignUpActivity"
    }
}