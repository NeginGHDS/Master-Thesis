package com.example.offline_first.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.offline_first.AppContainer
import com.example.offline_first.data.model.User
import com.example.offline_first.util.CryptoUtils
import com.example.offline_first.util.ioDispatcher
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SignUpUiState(
    val isLoading: Boolean = false,
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null
)

sealed interface SignUpEvent {
    data class SignedUp(val message: String) : SignUpEvent
    data class ShowMessage(val text: String) : SignUpEvent
}

/** Ported from `SignUpActivity`: Firebase account creation, then a local [User] cache + session-time write, unchanged. */
@OptIn(ExperimentalTime::class)
class SignUpViewModel(private val container: AppContainer) : ViewModel() {

    private val auth = container.auth
    private val userDao = container.userDao
    private val sessionStore = container.sessionStore

    private val _uiState = MutableStateFlow(SignUpUiState())
    val uiState: StateFlow<SignUpUiState> = _uiState.asStateFlow()

    private val _events = MutableStateFlow<SignUpEvent?>(null)
    val events: StateFlow<SignUpEvent?> = _events.asStateFlow()

    fun consumeEvent() {
        _events.value = null
    }

    fun signUp(email: String, password: String, confirmPassword: String) {
        val trimmedEmail = email.trim()

        val emailError = if (trimmedEmail.isEmpty()) "Email is required" else null
        val passwordError = if (password.isEmpty()) "Password is required" else null
        val confirmError = if (confirmPassword != password) "Passwords do not match" else null

        if (emailError != null || passwordError != null || confirmError != null) {
            _uiState.value = _uiState.value.copy(
                emailError = emailError,
                passwordError = passwordError,
                confirmPasswordError = confirmError
            )
            return
        }
        _uiState.value = SignUpUiState(isLoading = true)

        viewModelScope.launch {
            try {
                val result = auth.createUserWithEmailAndPassword(trimmedEmail, password)
                withContext(ioDispatcher) {
                    val salt = CryptoUtils.generateSalt()
                    val hash = CryptoUtils.hashPassword(password, salt)
                    userDao.upsert(
                        User(
                            email = trimmedEmail,
                            passwordHash = hash,
                            salt = salt,
                            displayName = result.user?.displayName
                        )
                    )
                }
                sessionStore.loginTimeMillis = Clock.System.now().toEpochMilliseconds()
                _uiState.value = _uiState.value.copy(isLoading = false)
                _events.value = SignUpEvent.SignedUp("Account created!")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
                _events.value = SignUpEvent.ShowMessage("Sign-up failed: ${e.message}")
            }
        }
    }
}
