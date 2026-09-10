package com.openaicodex.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.openaicodex.app.engine.GitHubAuthManager
import com.openaicodex.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Settings > Connectors > GitHub. Uses GitHubAuthManager's Device Flow:
 * request a code, show it to the user, open github.com/login/device for
 * them to enter it, poll until they've authorized. No client secret
 * involved anywhere in this screen or its backing manager.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubConnectorScreen(
    githubAuthManager: GitHubAuthManager,
    onBack: () -> Unit,
    onOpenVerificationUrl: (String) -> Unit
) {
    var isConnected by remember { mutableStateOf(githubAuthManager.isConnected()) }
    var connectedLogin by remember { mutableStateOf(githubAuthManager.connectedLogin()) }
    var deviceInfo by remember { mutableStateOf<GitHubAuthManager.DeviceCodeInfo?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isWorking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = PureBlack,
        topBar = {
            TopAppBar(
                title = { Text("GitHub", color = PureWhite, style = MaterialTheme.typography.titleLarge.copy(fontStyle = FontStyle.Italic)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Geri", tint = PureWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PureBlack)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            if (isConnected) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(PanelBlack, RoundedCornerShape(14.dp)).padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Bağlı", color = SuccessGreen, style = MaterialTheme.typography.labelLarge)
                        Text(connectedLogin ?: "GitHub hesabı", color = PureWhite, style = MaterialTheme.typography.bodyLarge)
                    }
                    TextButton(onClick = {
                        githubAuthManager.disconnect()
                        isConnected = false
                        connectedLogin = null
                    }) {
                        Text("Bağlantıyı kes", color = ErrorRed)
                    }
                }
            } else {
                Text(
                    "Codex'in senin adına GitHub üzerinde işlem yapabilmesi için hesabını bağla. İstenen \"repo\" izni, herkese açık ve özel tüm depolarına tam okuma/yazma erişimi sağlar — bu GitHub'ın en dar kapsamlı izni değildir, Codex'in commit/push/PR gibi işlemleri yapabilmesi için gereken izindir.",
                    color = MutedWhite,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))

                val info = deviceInfo
                if (info == null) {
                    Button(
                        onClick = {
                            isWorking = true
                            statusMessage = null
                            scope.launch {
                                val result = githubAuthManager.requestDeviceCode()
                                result.onSuccess { deviceInfo = it }
                                result.onFailure { statusMessage = it.message }
                                isWorking = false
                            }
                        },
                        enabled = !isWorking,
                        colors = ButtonDefaults.buttonColors(containerColor = PureWhite, contentColor = PureBlack),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        if (isWorking) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = PureBlack)
                        else Text("GitHub'a Bağlan")
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().background(CardBlack, RoundedCornerShape(14.dp)).padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Bu kodu girin", color = MutedWhite, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(info.userCode, color = PureWhite, style = MaterialTheme.typography.displayMedium.copy(fontStyle = FontStyle.Italic))
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { onOpenVerificationUrl(info.verificationUri) },
                            colors = ButtonDefaults.buttonColors(containerColor = PureWhite, contentColor = PureBlack),
                            shape = RoundedCornerShape(24.dp)
                        ) { Text("github.com/login/device Aç") }
                        Spacer(Modifier.height(12.dp))
                        Text("Kodu girdikten sonra otomatik olarak bağlanacak...", color = FaintWhite, style = MaterialTheme.typography.bodyMedium)

                        LaunchedEffect(info.deviceCode) {
                            val result = githubAuthManager.pollForToken(info.deviceCode, info.pollIntervalSeconds, info.expiresInSeconds)
                            result.onSuccess {
                                isConnected = true
                                connectedLogin = githubAuthManager.connectedLogin()
                                deviceInfo = null
                            }
                            result.onFailure {
                                statusMessage = it.message
                                deviceInfo = null
                            }
                        }
                    }
                }

                statusMessage?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = ErrorRed, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
