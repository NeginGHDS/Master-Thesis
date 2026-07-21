package com.example.offline_first.ui.screens.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.offline_first.viewmodel.LoginEvent
import com.example.offline_first.viewmodel.LoginViewModel

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onNavigateToHome: () -> Unit,
    onNavigateToSignUp: () -> Unit,
    onGoogleSignInRequested: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val event by viewModel.events.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(event) {
        when (val e = event) {
            is LoginEvent.NavigateToHome -> {
                viewModel.consumeEvent()
                onNavigateToHome()
            }
            is LoginEvent.NavigateToSignUp -> {
                viewModel.consumeEvent()
                onNavigateToSignUp()
            }
            is LoginEvent.ShowMessage -> {
                snackbarHostState.showSnackbar(e.text)
                viewModel.consumeEvent()
            }
            null -> Unit
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text("Offline-First", style = MaterialTheme.typography.headlineMedium)
                Text("Sign in to continue", style = MaterialTheme.typography.bodyMedium)

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    isError = uiState.emailError != null,
                    supportingText = { uiState.emailError?.let { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    isError = uiState.passwordError != null,
                    supportingText = { uiState.passwordError?.let { Text(it) } },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { viewModel.signInWithEmail(email, password) },
                    enabled = !uiState.isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Login")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onGoogleSignInRequested,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sign in with Google")
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { viewModel.onTwitterClicked() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sign in with Twitter")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(onClick = { viewModel.onSignUpClicked() }) {
                        Text("Don't have an account? Sign up")
                    }
                }
            }
        }
    }
}
