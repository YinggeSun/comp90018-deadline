package com.comp90018.deadline.feature.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comp90018.deadline.core.theme.Spacing
import com.comp90018.deadline.core.ui.components.PrimaryButton
import com.comp90018.deadline.core.ui.components.TertiaryButton
import com.comp90018.deadline.domain.repository.Account

enum class AccountPage { PROFILE, LOGIN, REGISTER }

@Composable
fun AccountScreen(
    page: AccountPage,
    onHome: () -> Unit,
    onProfile: () -> Unit,
    onLogin: () -> Unit,
    onRegister: () -> Unit,
    viewModel: AccountViewModel = viewModel(factory = AccountViewModel.Factory),
) {
    val account by viewModel.account.collectAsState()
    val action by viewModel.action.collectAsState()
    LaunchedEffect(action.canNavigate) {
        if (page != AccountPage.PROFILE && action.canNavigate) onProfile()
    }
    AccountContent(
        page,
        account,
        action,
        onHome,
        onLogin,
        onRegister,
        viewModel::login,
        viewModel::register,
        viewModel::nickname,
        viewModel::logout,
        viewModel::refresh,
    )
}

@Composable
fun AccountContent(
    page: AccountPage,
    account: Account,
    action: AccountActionState,
    onHome: () -> Unit,
    onLogin: () -> Unit,
    onRegister: () -> Unit,
    login: (String, String) -> Unit,
    register: (String, String, String, String) -> Unit,
    saveNickname: (String) -> Unit,
    logout: () -> Unit,
    refresh: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var nickname by remember(account.uid, account.nickname) { mutableStateOf(account.nickname.orEmpty()) }
    var visible by remember { mutableStateOf(false) }
    BackHandler(action.busy) { }
    LaunchedEffect(action.succeeded, account.registered) {
        if (action.succeeded || account.registered) {
            password = ""
            confirmation = ""
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        Text(
            when (page) {
                AccountPage.PROFILE -> "Account"
                AccountPage.LOGIN -> "Log In"
                AccountPage.REGISTER -> "Create Account"
            },
            style = MaterialTheme.typography.headlineMedium,
        )
        if (action.busy || action.syncing) CircularProgressIndicator()
        if (action.localPending || !account.storageReady) {
            Text("Account storage needs recovery. Retry account setup before saving progress.")
        }
        if (action.profilePending) Text("Signed in. Profile sync is pending; retry when online.")
        if (action.progressPending && !action.syncing) {
            Text(
                "Account operation succeeded. Progress sync is pending; playing remains available.",
            )
        }
        if (action.succeeded && !account.registered) Text("Playing as Guest. Your registered progress remains saved separately.")
        if (!account.registered && (action.localPending || !account.storageReady)) {
            TertiaryButton("Retry Account Setup", refresh, enabled = !action.busy && !action.syncing)
        }
        action.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        if (page == AccountPage.PROFILE || account.registered) {
            Text(
                if (account.registered) {
                    "Registered account"
                } else if (account.uid != null) {
                    "Playing as Guest"
                } else {
                    "Playing as Guest • Offline"
                },
            )
            if (account.registered) {
                Text(account.email.orEmpty())
                AccountField("Nickname", nickname, { nickname = it }, action.busy)
                PrimaryButton("Save Nickname", { saveNickname(nickname) }, enabled = !action.busy && !action.syncing)
                TertiaryButton("Retry Account Sync", refresh, enabled = !action.busy && !action.syncing)
                TertiaryButton("Log Out", logout, enabled = !action.busy && !action.syncing)
            } else {
                Text("Create an account to keep this guest identity and progress across devices.")
                PrimaryButton("Create Account", onRegister, enabled = !action.busy)
                TertiaryButton("Log In", onLogin, enabled = !action.busy)
            }
        } else {
            if (page == AccountPage.REGISTER) AccountField("Nickname", nickname, { nickname = it }, action.busy)
            AccountField("Email", email, { email = it }, action.busy, KeyboardType.Email)
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !action.busy,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { TertiaryButton(if (visible) "Hide" else "Show", { visible = !visible }) },
            )
            if (page == AccountPage.REGISTER) {
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = { confirmation = it },
                    label = { Text("Confirm password") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !action.busy,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                )
                PrimaryButton("Create Account", { register(nickname, email, password, confirmation) }, enabled = !action.busy)
                TertiaryButton("Back to Login", onLogin, enabled = !action.busy)
            } else {
                Text(
                    "Logging in opens that account's progress. Guest progress stays separate on this device. " +
                        "To move it explicitly, create a transfer code in Settings before logging in.",
                )
                PrimaryButton("Log In", { login(email, password) }, enabled = !action.busy)
                TertiaryButton("Create Account", onRegister, enabled = !action.busy)
            }
        }
        TertiaryButton(
            if (page == AccountPage.PROFILE || account.registered) "Continue Playing" else "Continue as Guest",
            onHome,
            enabled = !action.busy,
        )
    }
}

@Composable
private fun AccountField(
    label: String,
    value: String,
    change: (String) -> Unit,
    busy: Boolean,
    type: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value,
        change,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        enabled = !busy,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type),
    )
}
