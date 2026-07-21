package com.example.offline_first.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.offline_first.AppContainer
import com.example.offline_first.data.local.SessionStore
import com.example.offline_first.data.model.User
import com.example.offline_first.util.CryptoUtils
import com.example.offline_first.util.ioDispatcher
import com.example.offline_first.util.isNetworkOnline
import dev.gitlive.firebase.auth.GoogleAuthProvider
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LoginUiState(
    val isLoading: Boolean = false,
    val emailError: String? = null,
    val passwordError: String? = null,
    val message: String? = null
)

/** One-shot UI effects, mirroring the `Toast`s / navigation `startActivity` calls in the original `LoginActivity`. */
sealed interface LoginEvent {
    data object NavigateToHome : LoginEvent
    data object NavigateToSignUp : LoginEvent
    data class ShowMessage(val text: String) : LoginEvent
}

/**
 * Ported from `LoginActivity`. Preserves the online/offline branching exactly:
 * online → Firebase email/password sign-in (and caches a local credential for offline fallback);
 * offline → verify against the locally cached [User] via [CryptoUtils].
 *
 * Google Sign-In's credential acquisition is platform UI (Credential Manager on Android); this
 * ViewModel only owns what happens *after* an ID token is obtained ([onGoogleIdTokenReceived]),
 * keeping the Firebase exchange + navigation logic shared.
 */
@OptIn(ExperimentalTime::class)
class LoginViewModel(private val container: AppContainer) : ViewModel() {

    private val auth = container.auth
    private val userDao = container.userDao
    private val sessionStore = container.sessionStore

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _events = MutableStateFlow<LoginEvent?>(null)
    val events: StateFlow<LoginEvent?> = _events.asStateFlow()

    fun consumeEvent() {
        _events.value = null
    }

    fun onTwitterClicked() {
        _events.value = LoginEvent.ShowMessage("Twitter Login")
    }

    fun onSignUpClicked() {
        _events.value = LoginEvent.NavigateToSignUp
    }

    fun signInWithEmail(email: String, password: String) {
        val trimmedEmail = email.trim()

        var emailError: String? = null
        var passwordError: String? = null
        if (trimmedEmail.isEmpty()) emailError = "Email is required"
        if (password.isEmpty()) passwordError = "Password is required"
        if (emailError != null || passwordError != null) {
            _uiState.value = _uiState.value.copy(emailError = emailError, passwordError = passwordError)
            return
        }
        _uiState.value = _uiState.value.copy(emailError = null, passwordError = null, isLoading = true)

        viewModelScope.launch {
            if (!isNetworkOnline()) {
                val ok = withContext(ioDispatcher) { verifyLocalCredential(trimmedEmail, password) }
                _uiState.value = _uiState.value.copy(isLoading = false)
                if (ok) {
                    navigateToHome(localEmail = trimmedEmail)
                } else {
                    _events.value = LoginEvent.ShowMessage("Incorrect email or password.")
                }
                return@launch
            }

            try {
                val result = auth.signInWithEmailAndPassword(trimmedEmail, password)
                withContext(ioDispatcher) {
                    saveLocalCredential(trimmedEmail, password, result.user?.displayName)
                }
                _uiState.value = _uiState.value.copy(isLoading = false)
                navigateToHome()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
                _events.value = LoginEvent.ShowMessage("Sign-in failed: ${e.message}")
            }
        }
    }

    private suspend fun verifyLocalCredential(email: String, password: String): Boolean {
        val user = userDao.findByEmail(email) ?: return false
        return CryptoUtils.verify(password, user.salt, user.passwordHash)
    }

    private suspend fun saveLocalCredential(email: String, password: String, displayName: String?) {
        val salt = CryptoUtils.generateSalt()
        val hash = CryptoUtils.hashPassword(password, salt)
        userDao.upsert(User(email = email, passwordHash = hash, salt = salt, displayName = displayName))
    }

    /** Called by the platform UI once a Google ID token has been obtained (e.g. via Credential Manager on Android). */
    fun onGoogleIdTokenReceived(idToken: String) {
        viewModelScope.launch {
            try {
                val credential = GoogleAuthProvider.credential(idToken, null)
                auth.signInWithCredential(credential)
                navigateToHome()
            } catch (e: Exception) {
                _events.value = LoginEvent.ShowMessage("Auth failed: ${e.message}")
            }
        }
    }

    fun onGoogleSignInFailed(message: String?) {
        _events.value = LoginEvent.ShowMessage("Sign-in failed: $message")
    }

    private fun navigateToHome(localEmail: String? = null) {
        sessionStore.loginTimeMillis = Clock.System.now().toEpochMilliseconds()
        if (localEmail != null) sessionStore.localAuthEmail = localEmail
        else sessionStore.localAuthEmail = null
        _events.value = LoginEvent.NavigateToHome
    }

    companion object {
        const val SESSION_DURATION_MS = SessionStore.SESSION_DURATION_MS
    }
}
