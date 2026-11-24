package com.eyalm.adns

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.eyalm.adns.data.Locales
import com.eyalm.adns.data.controld.ControlDFailureKind
import com.eyalm.adns.data.controld.ControlDRepository
import com.eyalm.adns.data.controld.ControlDResult
import com.eyalm.adns.data.controld.ControlDTokenStore
import com.eyalm.adns.data.localization.AppLocaleRepository
import com.eyalm.adns.ui.theme.AdnsTheme
import kotlinx.coroutines.launch

/** Pantalla para iniciar sesión en ControlD pegando un token de API. */
class ControlDLoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Locales.sync(this, AppLocaleRepository(this).selectedTag())
        enableEdgeToEdge()
        setContent {
            AdnsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ControlDLoginScreen(onBack = { finish() })
                }
            }
        }
    }
}

@Composable
private fun ControlDLoginScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { ControlDTokenStore.getInstance(context) }
    val scope = rememberCoroutineScope()
    var connected by remember { mutableStateOf(store.hasToken()) }
    var email by remember { mutableStateOf(store.getEmail()) }
    var token by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<ControlDFailureKind?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.controld_back),
                )
            }
            Text(
                text = stringResource(R.string.controld_title),
                style = MaterialTheme.typography.titleLarge,
            )
        }

        if (connected) {
            Text(
                text = stringResource(R.string.controld_connected),
                style = MaterialTheme.typography.titleMedium,
            )
            email?.let { Text(text = it) }
            OutlinedButton(
                onClick = {
                    store.clear()
                    connected = false
                    email = null
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.controld_sign_out)) }
        } else {
            Text(text = stringResource(R.string.controld_token_help))
            OutlinedTextField(
                value = token,
                onValueChange = { token = it; error = null },
                label = { Text(stringResource(R.string.controld_token_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                isError = error != null,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let {
                Text(
                    text = stringResource(
                        when (it) {
                            ControlDFailureKind.InvalidToken -> R.string.controld_error_invalid
                            ControlDFailureKind.Forbidden -> R.string.controld_error_forbidden
                            ControlDFailureKind.Network -> R.string.controld_error_network
                            ControlDFailureKind.Server,
                            ControlDFailureKind.BadResponse -> R.string.controld_error_other
                        }
                    ),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = {
                    val clean = token.trim()
                    if (clean.isEmpty() || loading) return@Button
                    loading = true
                    error = null
                    scope.launch {
                        val result = ControlDRepository(tokenProvider = { clean }).getAccount()
                        loading = false
                        when (result) {
                            is ControlDResult.Success -> {
                                store.saveToken(clean)
                                store.saveEmail(result.value.email)
                                email = result.value.email
                                token = ""
                                connected = true
                            }

                            is ControlDResult.Failure -> error = result.kind
                        }
                    }
                },
                enabled = token.isNotBlank() && !loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (loading) R.string.controld_checking else R.string.controld_check_and_save
                    )
                )
            }
        }
    }
}
