package com.mj.spendwise.ui.screens.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mj.spendwise.util.AuthValidation
import com.mj.spendwise.viewmodel.ExpenseViewModel

/**
 * Login / register screen. Email + password (Firebase Auth), password reset, and "Continue as guest"
 * (Anonymous Auth). When a uid appears the screen calls [onLoggedIn]; nothing else navigates from here.
 */
@Composable
fun LoginScreen(vm: ExpenseViewModel, onLoggedIn: () -> Unit, modifier: Modifier = Modifier) {
    val uid by vm.uid.collectAsStateWithLifecycle()
    val startup by vm.startup.collectAsStateWithLifecycle()

    var registering by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var info by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(uid) { if (uid != null) onLoggedIn() }

    val fail: (String) -> Unit = { message ->
        busy = false
        info = null
        error = message
    }

    fun submit() {
        val problem = AuthValidation.problem(email, password, if (registering) confirm else null)
        if (problem != null) {
            fail(problem)
            return
        }
        busy = true
        error = null
        info = null
        if (registering) vm.register(email, password, fail) else vm.signIn(email, password, fail)
    }

    fun resetPassword() {
        if (!AuthValidation.isValidEmail(email)) {
            fail("Enter your email above first, then tap \"Forgot password?\".")
            return
        }
        busy = true
        error = null
        vm.sendPasswordReset(
            email,
            onDone = { busy = false; info = "Password reset email sent to ${email.trim()}. Check your inbox." },
            onError = fail
        )
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Text("SpendWise", style = MaterialTheme.typography.headlineLarge)
        Text(
            if (registering) "Create your account" else "Sign in to sync your expenses",
            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center
        )

        (startup as? ExpenseViewModel.Startup.Failed)?.let {
            Text(it.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }

        OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text("Email") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("Password") }, singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = if (registering) ImeAction.Next else ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { if (!registering) submit() }),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showPassword) "Hide password" else "Show password"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        if (registering) {
            OutlinedTextField(
                value = confirm, onValueChange = { confirm = it },
                label = { Text("Confirm password") }, singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth()
            )
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
        info?.let { Text(it, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center) }

        Button(onClick = ::submit, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text(if (registering) "Create account" else "Sign in")
        }
        if (!registering) TextButton(onClick = ::resetPassword, enabled = !busy) { Text("Forgot password?") }
        TextButton(
            onClick = { registering = !registering; error = null; info = null },
            enabled = !busy
        ) { Text(if (registering) "Already have an account? Sign in" else "New here? Create an account") }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        OutlinedButton(
            onClick = { busy = true; error = null; vm.continueAsGuest(fail) },
            enabled = !busy, modifier = Modifier.fillMaxWidth()
        ) { Text("Continue as guest") }
        Text(
            "Guest data stays on this account only. You can add an email later in Settings to keep it.",
            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center
        )
    }
}
