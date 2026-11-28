package com.eyalm.adns

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eyalm.adns.data.controld.ControlDEndpoint
import com.eyalm.adns.data.controld.ControlDFilter
import androidx.compose.material3.Switch
import com.eyalm.adns.data.provider.DnsProviderSelection
import com.eyalm.adns.data.provider.PrivateDnsHostname
import com.eyalm.adns.data.provider.SavedCustomDnsStore
import com.eyalm.adns.viewmodel.SettingsViewModel
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.collectAsState
import com.eyalm.adns.ui.components.ActionSettingRow
import com.eyalm.adns.ui.components.ResourceSettingRow
import com.eyalm.adns.ui.components.SegmentPosition
import com.eyalm.adns.ui.components.segmentPosition
import com.eyalm.adns.ui.screens.settings.SettingsScreenScaffold
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
    var token by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<ControlDFailureKind?>(null) }
    val settingsViewModel: SettingsViewModel = viewModel()
    val currentSelection by settingsViewModel.selectedProvider.collectAsState()
    val endpoints = remember { mutableStateListOf<ControlDEndpoint>() }
    var endpointsLoading by remember { mutableStateOf(false) }
    var endpointsFailed by remember { mutableStateOf(false) }
    val profileFilters = remember { mutableStateMapOf<String, List<ControlDFilter>?>() }
    var editingProfile by remember { mutableStateOf<String?>(null) }
    var filterChangeFailed by remember { mutableStateOf(false) }

    LaunchedEffect(connected) {
        if (!connected) return@LaunchedEffect
        endpointsLoading = true
        endpointsFailed = false
        val result = ControlDRepository(tokenProvider = { store.getToken() }).getEndpoints()
        endpointsLoading = false
        when (result) {
            is ControlDResult.Success -> {
                endpoints.clear()
                endpoints.addAll(result.value)
            }
            is ControlDResult.Failure -> endpointsFailed = true
        }
        endpoints.mapNotNull { it.profileId }.distinct().forEach { id ->
            profileFilters[id] = (ControlDRepository(tokenProvider = { store.getToken() })
                .getFilters(id) as? ControlDResult.Success)?.value
        }
    }

    SettingsScreenScaffold(
        onBack = onBack,
        title = stringResource(R.string.controld_title),
        description = stringResource(
            if (connected) R.string.controld_endpoints_help else R.string.controld_token_help
        ),
    ) {
        if (connected) {
            if (endpointsLoading) {
                item(key = "cd_loading") {
                    CircularProgressIndicator(modifier = Modifier.padding(vertical = 16.dp))
                }
            }
            if (endpointsFailed) {
                item(key = "cd_error") {
                    Text(
                        text = stringResource(R.string.controld_endpoints_error),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (!endpointsLoading && !endpointsFailed && endpoints.isEmpty()) {
                item(key = "cd_empty") { Text(text = stringResource(R.string.controld_endpoints_empty)) }
            }
            itemsIndexed(endpoints.toList(), key = { _, e -> "cd_ep_" + e.id }) { index, endpoint ->
                val host = endpoint.dotHost?.let { PrivateDnsHostname.parse(it)?.ascii }
                val selected = host != null &&
                    (currentSelection as? DnsProviderSelection.Custom)?.hostname == host
                val lines = listOfNotNull(
                    endpoint.profileName,
                    host ?: stringResource(R.string.controld_endpoint_no_host),
                ).joinToString("\n")
                ResourceSettingRow(
                    title = endpoint.name,
                    description = lines,
                    selected = selected,
                    enabled = host != null,
                    position = segmentPosition(index, endpoints.size),
                    alignment = Alignment.CenterVertically,
                    leading = { RadioButton(selected = selected, onClick = null) },
                    onClick = {
                        host?.let {
                            SavedCustomDnsStore.add(context, it)
                            settingsViewModel.setProvider(DnsProviderSelection.Custom(it))
                        }
                    },
                )
            }

            val profiles = endpoints.mapNotNull { e -> e.profileId?.let { it to e.profileName } }
                .distinctBy { it.first }
            profiles.forEach { (pid, pname) ->
                val list = profileFilters[pid]
                if (!profileFilters.containsKey(pid)) return@forEach
                item(key = "cd_pf_head_$pid") {
                    Text(
                        text = pname ?: pid,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                    )
                }
                val active = list?.filter { it.enabled }
                item(key = "cd_pf_sum_$pid") {
                    ResourceSettingRow(
                        title = when {
                            list == null -> stringResource(R.string.controld_filters_error)
                            active.isNullOrEmpty() -> stringResource(R.string.controld_filters_none)
                            else -> stringResource(R.string.controld_filters_active)
                        },
                        description = active?.takeIf { it.isNotEmpty() }
                            ?.joinToString(", ") { it.name },
                        position = if (!list.isNullOrEmpty()) SegmentPosition.First else SegmentPosition.Single,
                    )
                }
                if (!list.isNullOrEmpty()) {
                    val editing = editingProfile == pid
                    item(key = "cd_pf_edit_$pid") {
                        ActionSettingRow(
                            title = stringResource(
                                if (editing) R.string.controld_filters_done else R.string.controld_filters_edit
                            ),
                            position = if (editing) SegmentPosition.Middle else SegmentPosition.Last,
                            onClick = {
                                editingProfile = if (editing) null else pid
                                filterChangeFailed = false
                            },
                        )
                    }
                    if (editing) {
                        if (filterChangeFailed) {
                            item(key = "cd_pf_err_$pid") {
                                Text(
                                    text = stringResource(R.string.controld_filter_change_error),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                )
                            }
                        }
                        itemsIndexed(list, key = { _, f -> "cd_pf_${pid}_" + f.id }) { fi, filter ->
                            ResourceSettingRow(
                                title = filter.name,
                                position = if (fi == list.size - 1) SegmentPosition.Last else SegmentPosition.Middle,
                                alignment = Alignment.CenterVertically,
                                trailing = {
                                    Switch(checked = filter.enabled, onCheckedChange = null)
                                },
                                onClick = {
                                    val wanted = !filter.enabled
                                    filterChangeFailed = false
                                    scope.launch {
                                        val result = ControlDRepository(
                                            tokenProvider = { store.getToken() }
                                        ).setFilterEnabled(pid, filter.id, wanted)
                                        if (result is ControlDResult.Success) {
                                            profileFilters[pid] = profileFilters[pid]?.map {
                                                if (it.id == filter.id) it.copy(enabled = wanted) else it
                                            }
                                        } else {
                                            filterChangeFailed = true
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }

            item(key = "cd_signout") {
                Column(modifier = Modifier.padding(top = 24.dp)) {
                    ActionSettingRow(
                        title = stringResource(R.string.controld_sign_out),
                        onClick = {
                            store.clear()
                            endpoints.clear()
                            profileFilters.clear()
                            editingProfile = null
                            connected = false
                        },
                    )
                }
            }
        } else {
            item(key = "cd_token") {
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it; error = null },
                    label = { Text(stringResource(R.string.controld_token_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = error != null,
                    enabled = !loading,
                    shape = RoundedCornerShape(12.dp),
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
                        modifier = Modifier.padding(top = 8.dp),
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
                                    token = ""
                                    connected = true
                                }

                                is ControlDResult.Failure -> error = result.kind
                            }
                        }
                    },
                    enabled = token.isNotBlank() && !loading,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .height(48.dp),
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
}
