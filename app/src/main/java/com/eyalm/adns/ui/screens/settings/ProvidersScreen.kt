package com.eyalm.adns.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.platform.LocalContext
import com.eyalm.adns.data.provider.SavedCustomDnsStore
import com.eyalm.adns.ui.components.ResourceSettingRow
import com.eyalm.adns.ui.components.segmentPosition
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eyalm.adns.R
import com.eyalm.adns.data.provider.DnsProviderCatalog
import com.eyalm.adns.data.provider.DnsProviderSelection
import com.eyalm.adns.data.provider.PrivateDnsHostname
import com.eyalm.adns.ui.components.settings.ProviderSelection
import com.eyalm.adns.ui.components.settings.ProviderPresetSelection
import com.eyalm.adns.ui.components.settings.ProviderSelectionAction
import com.eyalm.adns.ui.theme.pageTitle
import com.eyalm.adns.viewmodel.SettingsViewModel
import kotlinx.coroutines.delay

private enum class ProviderScreenPage {
    Providers,
    Presets,
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProvidersScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onEnhancedModeClick: (providerId: String) -> Unit = {},
) {
    val viewModel: SettingsViewModel = viewModel()
    val currentSelection by viewModel.selectedProvider.collectAsState()
    val catalog = remember { DnsProviderCatalog.default }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val savedDns = remember { mutableStateListOf<String>().apply { addAll(SavedCustomDnsStore.list(context)) } }
    LaunchedEffect(currentSelection) {
        (currentSelection as? DnsProviderSelection.Custom)?.hostname?.let { host ->
            if (host !in savedDns) {
                savedDns.clear()
                savedDns.addAll(SavedCustomDnsStore.add(context, host))
            }
        }
    }
    val isKeyboardVisible = WindowInsets.isImeVisible
    var isCustomSelected by remember(currentSelection) {
        mutableStateOf(currentSelection is DnsProviderSelection.Custom)
    }
    var customHostname by remember(currentSelection) {
        mutableStateOf(
            (currentSelection as? DnsProviderSelection.Custom)?.hostname.orEmpty()
        )
    }
    val normalizedCustomHostname = remember(customHostname) {
        PrivateDnsHostname.parse(customHostname)?.ascii
    }
    var presetPage by remember {
        mutableStateOf<ProviderSelectionAction.OpenPresets?>(null)
    }
    var screen by remember { mutableStateOf(ProviderScreenPage.Providers) }

    BackHandler(enabled = screen == ProviderScreenPage.Presets) {
        screen = ProviderScreenPage.Providers
    }

    LaunchedEffect(isKeyboardVisible, isCustomSelected) {
        if (isKeyboardVisible || isCustomSelected) {
            delay(200)
            val lastIndex = listState.layoutInfo.totalItemsCount - 1
            if (lastIndex >= 0) listState.animateScrollToItem(lastIndex)
        }
    }

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            if (targetState == ProviderScreenPage.Presets) {
                (fadeIn(animationSpec = tween(220, delayMillis = 90)) +
                    scaleIn(initialScale = 0.92f, animationSpec = tween(300)) +
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Up,
                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                        initialOffset = { it / 8 },
                    )) togetherWith
                    (fadeOut(animationSpec = tween(90)) +
                        scaleOut(targetScale = 1.08f, animationSpec = tween(300)))
            } else {
                (fadeIn(animationSpec = tween(220, delayMillis = 90)) +
                    scaleIn(initialScale = 1.08f, animationSpec = tween(300))) togetherWith
                    (fadeOut(animationSpec = tween(90)) +
                        scaleOut(targetScale = 0.92f, animationSpec = tween(300)) +
                        slideOutOfContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Down,
                            animationSpec = tween(300, easing = FastOutSlowInEasing),
                            targetOffset = { it / 8 },
                        ))
            }.using(SizeTransform(clip = false))
        },
        label = "ProviderSettingsPage",
    ) { targetPage ->
        when (targetPage) {
            ProviderScreenPage.Presets -> presetPage?.let { page ->
                ProviderPresetOptionsScreen(
                    page = page,
                    onBack = { screen = ProviderScreenPage.Providers },
                    onPresetSelected = { presetId ->
                        presetPage = page.copy(selectedPresetId = presetId)
                        viewModel.setProvider(
                            DnsProviderSelection.Standard(page.provider.id, presetId)
                        )
                    },
                )
            }

            ProviderScreenPage.Providers -> SettingsScreenLayout(
                title = stringResource(R.string.providers),
                onBack = onBack,
                showAppBarTitle = false,
                modifier = modifier.fillMaxSize(),
            ) { innerPadding ->
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = innerPadding.calculateTopPadding(),
                        bottom = innerPadding.calculateBottomPadding() + 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item {
                        Text(
                            text = stringResource(R.string.providers),
                            style = MaterialTheme.typography.pageTitle,
                            modifier = Modifier.padding(top = 24.dp),
                        )
                    }
                    item {
                        Text(
                            text = stringResource(R.string.select_your_preferred_dns_provider),
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }

                    ProviderSelection(
                        catalog = catalog,
                        currentSelection = currentSelection,
                        isCustomSelected = isCustomSelected,
                        onProviderClick = { action ->
                            when (action) {
                                is ProviderSelectionAction.OpenPresets -> {
                                    presetPage = action
                                    screen = ProviderScreenPage.Presets
                                }

                                is ProviderSelectionAction.Apply -> {
                                    val selection = action.selection
                                    if (
                                        selection is DnsProviderSelection.Enhanced &&
                                        !viewModel.isLoggedIn(selection.providerId)
                                    ) {
                                        onEnhancedModeClick(selection.providerId.value)
                                    } else {
                                        viewModel.setProvider(selection)
                                    }
                                }
                            }
                            isCustomSelected = false
                        },
                        customHostname = customHostname,
                        onCustomHostnameChange = { customHostname = it },
                        onCustomClick = { isCustomSelected = true },
                        onCustomConfirm = {
                            normalizedCustomHostname?.let { hostname ->
                                savedDns.clear()
                                savedDns.addAll(SavedCustomDnsStore.add(context, hostname))
                                viewModel.setProvider(
                                    DnsProviderSelection.Custom(hostname)
                                )
                                onBack()
                            }
                        },
                        isCustomHostnameValid = normalizedCustomHostname != null,
                        controlDTitle = stringResource(R.string.controld_provider_row),
                        controlDDescription = stringResource(R.string.controld_provider_row_desc),
                        onControlDClick = {
                            context.startActivity(
                                android.content.Intent(
                                    context,
                                    com.eyalm.adns.ControlDLoginActivity::class.java,
                                )
                            )
                        },
                    )

                    if (savedDns.isNotEmpty()) {
                        item(key = "saved_dns_header") {
                            Text(
                                text = stringResource(R.string.saved_dns_servers),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                            )
                        }
                        itemsIndexed(savedDns.toList(), key = { _, host -> "saved_$host" }) { index, host ->
                            val selected = (currentSelection as? DnsProviderSelection.Custom)?.hostname == host
                            val savedName = SavedCustomDnsStore.getName(context, host)
                            ResourceSettingRow(
                                title = savedName ?: host,
                                description = if (savedName != null) host else null,
                                selected = selected,
                                position = segmentPosition(index, savedDns.size),
                                onClick = { viewModel.setProvider(DnsProviderSelection.Custom(host)) },
                                leading = { RadioButton(selected = selected, onClick = null) },
                                trailing = {
                                    IconButton(onClick = {
                                        savedDns.clear()
                                        savedDns.addAll(SavedCustomDnsStore.remove(context, host))
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.delete_saved_dns),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderPresetOptionsScreen(
    page: ProviderSelectionAction.OpenPresets,
    onBack: () -> Unit,
    onPresetSelected: (com.eyalm.adns.data.provider.ResolverPresetId) -> Unit,
) {
    SettingsScreenScaffold(
        onBack = onBack,
        title = stringResource(page.provider.titleRes),
        description = stringResource(page.provider.descriptionRes),
    ) {
        ProviderPresetSelection(
            provider = page.provider,
            selectedPresetId = page.selectedPresetId,
            onPresetClick = onPresetSelected,
        )
    }
}
