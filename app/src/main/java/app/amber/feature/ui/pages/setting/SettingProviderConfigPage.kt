package app.amber.feature.ui.pages.setting

import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.amber.ai.provider.GoogleAuthMode
import app.amber.ai.provider.OpenAIAuthMode
import app.amber.ai.provider.ProviderSetting
import app.amber.ai.provider.availableAuthModes
import app.amber.ai.provider.fixedBaseUrl
import app.amber.ai.provider.providers.grok.GROK_CLI_PROXY_BASE_URL
import app.amber.ai.provider.providers.grok.GrokAuthStore
import app.amber.ai.provider.providers.grok.GrokAuthStatus
import app.amber.ai.provider.providers.grok.GrokOAuthClient
import app.amber.ai.provider.providers.grok.GrokOAuthTokens
import app.amber.agent.R
import app.amber.feature.ui.components.ui.SiliconFlowPowerByIcon
import app.amber.feature.ui.context.LocalToaster
import app.amber.feature.ui.pages.setting.components.ProviderCard
import app.amber.feature.ui.pages.setting.components.ProviderCommandButton
import app.amber.feature.ui.pages.setting.components.ProviderSecretField
import app.amber.feature.ui.pages.setting.components.ProviderSectionLabel
import app.amber.feature.ui.pages.setting.components.ProviderSegOption
import app.amber.feature.ui.pages.setting.components.ProviderTextField

import app.amber.feature.ui.pages.setting.provider.resetBaseUrlToDefault
import app.amber.feature.ui.theme.LocalAmberTokens
import app.amber.feature.ui.theme.LocalAmberType
import com.dokar.sonner.ToastType
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.koin.compose.koinInject

import app.amber.feature.ui.pages.setting.provider.ProviderDeleteDialog
import app.amber.feature.ui.pages.setting.provider.ProviderConsoleIdentity
import app.amber.feature.ui.pages.setting.provider.ProviderLabeledField
import app.amber.feature.ui.pages.setting.provider.ProviderConfigCapsule
import app.amber.feature.ui.pages.setting.provider.ProviderConsoleActions
import app.amber.feature.ui.pages.setting.provider.ProviderSwitchRow
import app.amber.feature.ui.pages.setting.provider.ProviderProtocolSection
import app.amber.feature.ui.pages.setting.provider.ClaudeAuthConsole
import app.amber.feature.ui.pages.setting.provider.ProviderEndpointSection
import app.amber.feature.ui.pages.setting.provider.ProviderAccountSection
import app.amber.feature.ui.pages.setting.provider.isCodingPlan

@Composable
internal fun SettingProviderConfigPage(
    internalProvider: ProviderSetting,
    onInternalChange: (ProviderSetting) -> Unit,
    onEdit: (ProviderSetting) -> Unit,
    onDelete: () -> Unit,
    onModelsFetched: (ProviderModelCandidates) -> Unit = {},
    onModelCandidatesInvalidated: () -> Unit = {},
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    ProviderConsole(
        provider = internalProvider,
        onEdit = onInternalChange,
        // OAuth consoles commit whole-provider snapshots whose model writes are
        // intentional (login selection, fetched defaults); the draft's models are
        // pinned to the store upstream, so commit as-is.
        onCommit = {
            onInternalChange(it)
            onEdit(it)
        },
        onModelsFetched = onModelsFetched,
        onModelCandidatesInvalidated = onModelCandidatesInvalidated,
    ) { currentProvider ->
        ProviderConsoleActions(
            provider = currentProvider,
            onDelete = { showDeleteDialog = true },
            onReset = { onInternalChange(internalProvider.resetBaseUrlToDefault()) },
            onSave = { onEdit(internalProvider) },
        )
    }

    if (showDeleteDialog) {
        ProviderDeleteDialog(
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                onDelete()
            },
        )
    }
}

@Composable
internal fun ProviderConsole(
    provider: ProviderSetting,
    onEdit: (ProviderSetting) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    autoStartOAuth: Boolean = false,
    onAutoStartConsumed: () -> Unit = {},
    onCommit: (ProviderSetting) -> Unit = onEdit,
    onModelsFetched: (ProviderModelCandidates) -> Unit = {},
    onModelCandidatesInvalidated: () -> Unit = {},
    actionContent: (@Composable (ProviderSetting) -> Unit)? = null,
) {
    val grokTokens = if (provider is ProviderSetting.OpenAI && provider.authMode == OpenAIAuthMode.GROK_OAUTH) {
        rememberGrokOAuthTokens(provider)
    } else null
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .background(Color.Transparent),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item("identity") {
            ProviderCard(modifier = Modifier.fillMaxWidth()) {
                ProviderConsoleIdentity(
                    provider = provider,
                    onEdit = onEdit,
                    oauthUsable = grokTokens?.takeIf { it.loaded.value }?.let {
                        GrokAuthStatus.from(it.tokens.value, System.currentTimeMillis()).usable
                    },
                )
            }
        }
        item("protocol") {
            ProviderProtocolSection(
                provider = provider,
                onEdit = onEdit,
            )
        }
        item("auth") {
            ProviderAuthSection(
                provider = provider,
                onEdit = onEdit,
                onCommit = onCommit,
                onModelsFetched = onModelsFetched,
                onModelCandidatesInvalidated = onModelCandidatesInvalidated,
                autoStartOAuth = autoStartOAuth,
                onAutoStartConsumed = onAutoStartConsumed,
                grokTokens = grokTokens,
            )
        }
        item("endpoint") {
            val googleServiceAccountImport = if (provider is ProviderSetting.Google) {
                rememberGoogleServiceAccountImport(provider, onEdit)
            } else null
            ProviderEndpointSection(
                provider = provider,
                onEdit = onEdit,
                onImportGoogleServiceAccount = {
                    googleServiceAccountImport?.launch(arrayOf("application/json", "*/*"))
                },
            )
        }
        if (provider is ProviderSetting.OpenAI) {
            item("account") {
                ProviderAccountSection(
                    provider = provider,
                    onEdit = onEdit,
                )
            }
        }
        if (actionContent != null) {
            item("actions") {
                actionContent(provider)
            }
        }
        val siliconFlowProvider = provider as? ProviderSetting.OpenAI
        if (siliconFlowProvider?.baseUrl?.contains("siliconflow.cn") == true) {
            item("siliconflow") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    SiliconFlowPowerByIcon()
                }
            }
        }
    }
}

@Composable
private fun ProviderAuthSection(
    provider: ProviderSetting,
    onEdit: (ProviderSetting) -> Unit,
    onCommit: (ProviderSetting) -> Unit,
    onModelsFetched: (ProviderModelCandidates) -> Unit,
    onModelCandidatesInvalidated: () -> Unit,
    autoStartOAuth: Boolean,
    onAutoStartConsumed: () -> Unit,
    grokTokens: GrokOAuthTokenState?,
) {
    ProviderSectionLabel(stringResource(R.string.setting_provider_page_authentication))
    ProviderCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (provider) {
                is ProviderSetting.OpenAI -> OpenAIAuthConsole(
                    provider = provider,
                    onEdit = onEdit,
                    onCommit = onCommit,
                    onModelsFetched = onModelsFetched,
                    onModelCandidatesInvalidated = onModelCandidatesInvalidated,
                    autoStartOAuth = autoStartOAuth,
                    onAutoStartConsumed = onAutoStartConsumed,
                    grokTokens = grokTokens,
                )
                is ProviderSetting.Google -> GoogleAuthConsole(
                    provider = provider,
                    onEdit = onEdit,
                    onCommit = onCommit,
                    autoStartOAuth = autoStartOAuth,
                    onAutoStartConsumed = onAutoStartConsumed,
                )
                is ProviderSetting.Claude -> ClaudeAuthConsole(
                    provider = provider,
                    onEdit = onEdit,
                )
            }
        }
    }
}

@Composable
private fun OpenAIAuthConsole(
    provider: ProviderSetting.OpenAI,
    onEdit: (ProviderSetting.OpenAI) -> Unit,
    onCommit: (ProviderSetting.OpenAI) -> Unit,
    onModelsFetched: (ProviderModelCandidates) -> Unit,
    onModelCandidatesInvalidated: () -> Unit,
    autoStartOAuth: Boolean,
    onAutoStartConsumed: () -> Unit,
    grokTokens: GrokOAuthTokenState?,
) {
    val isGrok = provider.isGrokProvider()
    val grokStore = if (isGrok) koinInject<GrokAuthStore>() else null
    val availableModes = if (isGrok) {
        listOf(OpenAIAuthMode.API_KEY, OpenAIAuthMode.GROK_OAUTH)
    } else provider.brand.availableAuthModes()
    if (availableModes.size > 1) {
        ProviderConfigCapsule(
            options = availableModes.map { ProviderSegOption(it, it.openAIAuthLabel()) },
            selected = provider.authMode,
            onSelected = { mode ->
                if (provider.authMode != mode) {
                    if (mode == OpenAIAuthMode.GROK_OAUTH) {
                        grokStore?.clearBackup(provider.id)
                        grokStore?.saveBackup(provider.id, provider.baseUrl)
                    }
                    onEdit(provider.switchOpenAIAuthMode(mode, grokStore?.getBackup(provider.id)))
                }
            },
        )
    }
    if (provider.authMode !in availableModes) {
        LaunchedEffect(provider.id, provider.brand) {
            onEdit(provider.copy(authMode = OpenAIAuthMode.API_KEY))
        }
    }

    val isCodingPlan = provider.authMode.isCodingPlan()
    when {
        provider.authMode == OpenAIAuthMode.CODEX_OAUTH -> {
            CodexOAuthConsole(
                provider = provider,
                onCommit = onCommit,
                onModelsFetched = onModelsFetched,
                onModelCandidatesInvalidated = onModelCandidatesInvalidated,
                autoStartOAuth = autoStartOAuth,
                onAutoStartConsumed = onAutoStartConsumed,
            )
        }
        provider.authMode == OpenAIAuthMode.GROK_OAUTH -> {
            GrokOAuthConsole(
                provider = provider,
                tokenState = checkNotNull(grokTokens),
                onCommit = onCommit,
                autoStartOAuth = autoStartOAuth,
                onAutoStartConsumed = onAutoStartConsumed,
            )
        }
        provider.authMode == OpenAIAuthMode.API_KEY || isCodingPlan -> {
            ProviderLabeledField("API Key") {
                ProviderSecretField(
                    value = provider.apiKey,
                    onValueChange = { onEdit(provider.copy(apiKey = it.trim())) },
                    placeholder = "sk-...",
                )
            }
        }
    }
}

@Composable
private fun GoogleAuthConsole(
    provider: ProviderSetting.Google,
    onEdit: (ProviderSetting.Google) -> Unit,
    onCommit: (ProviderSetting.Google) -> Unit,
    autoStartOAuth: Boolean,
    onAutoStartConsumed: () -> Unit,
) {
    ProviderConfigCapsule(
        options = listOf(
            ProviderSegOption(GoogleAuthMode.API_KEY, "API Key"),
            ProviderSegOption(GoogleAuthMode.GEMINI_CODE_ASSIST_OAUTH, "Code Assist"),
            ProviderSegOption(GoogleAuthMode.ANTIGRAVITY_OAUTH, "Antigravity"),
        ),
        selected = provider.authMode,
        onSelected = { mode -> onEdit(provider.switchGoogleAuthMode(mode)) },
    )

    when (provider.authMode) {
        GoogleAuthMode.API_KEY -> {
            if (!(provider.vertexAI && provider.useServiceAccount)) {
                ProviderLabeledField("API Key") {
                    ProviderSecretField(
                        value = provider.apiKey,
                        onValueChange = { onEdit(provider.copy(apiKey = it.trim())) },
                        placeholder = "AIza...",
                    )
                }
            }
            ProviderSwitchRow(
                title = stringResource(R.string.setting_provider_page_vertex_ai),
                checked = provider.vertexAI,
                onCheckedChange = { onEdit(provider.copy(vertexAI = it)) },
            )
            if (provider.vertexAI) {
                ProviderSwitchRow(
                    title = stringResource(R.string.setting_provider_page_use_service_account),
                    checked = provider.useServiceAccount,
                    onCheckedChange = { onEdit(provider.copy(useServiceAccount = it)) },
                )
            }
        }
        GoogleAuthMode.GEMINI_CODE_ASSIST_OAUTH -> {
            GeminiOAuthConsole(
                provider = provider,
                onCommit = onCommit,
                autoStartOAuth = autoStartOAuth,
                onAutoStartConsumed = onAutoStartConsumed,
            )
        }
        GoogleAuthMode.ANTIGRAVITY_OAUTH -> {
            AntigravityOAuthConsole(
                provider = provider,
                onCommit = onCommit,
                autoStartOAuth = autoStartOAuth,
                onAutoStartConsumed = onAutoStartConsumed,
            )
        }
    }
}

private data class GrokOAuthTokenState(
    val tokens: MutableState<GrokOAuthTokens?>,
    val loaded: State<Boolean>,
    val readGeneration: MutableState<Int>,
)

@Composable
private fun rememberGrokOAuthTokens(provider: ProviderSetting.OpenAI): GrokOAuthTokenState {
    val store = koinInject<GrokAuthStore>()
    val lifecycleOwner = LocalLifecycleOwner.current
    val tokens = remember(provider.id, store) { mutableStateOf<GrokOAuthTokens?>(null) }
    val loaded = remember(provider.id, store) { mutableStateOf(false) }
    val readGeneration = remember(provider.id, store) { mutableStateOf(0) }
    LaunchedEffect(provider.id, store, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val requestGeneration = readGeneration.value + 1
            readGeneration.value = requestGeneration
            val cached = withContext(Dispatchers.IO) { store.get(provider.id) }
            if (readGeneration.value == requestGeneration) {
                tokens.value = cached
                loaded.value = true
            }
        }
    }
    return GrokOAuthTokenState(tokens, loaded, readGeneration)
}

@Composable
private fun GrokOAuthConsole(
    provider: ProviderSetting.OpenAI,
    tokenState: GrokOAuthTokenState,
    onCommit: (ProviderSetting.OpenAI) -> Unit,
    autoStartOAuth: Boolean,
    onAutoStartConsumed: () -> Unit,
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val httpClient = koinInject<OkHttpClient>()
    val store = koinInject<GrokAuthStore>()
    val client = remember(httpClient, store) { GrokOAuthClient(httpClient, store) }
    var tokens by tokenState.tokens
    val latestProvider by rememberUpdatedState(provider)
    LaunchedEffect(provider.id, provider.authMode) {
        if (provider.authMode == OpenAIAuthMode.GROK_OAUTH) {
            withContext(Dispatchers.IO) {
                if (store.getBackup(provider.id) == null) {
                    store.saveBackup(provider.id, provider.baseUrl)
                }
            }
        }
    }
    var busy by remember(provider.id) { mutableStateOf(false) }

    suspend fun login() {
        if (busy) return
        tokenState.readGeneration.value += 1
        busy = true
        try {
            val result = client.authorize(context, provider.id)
            val current = latestProvider
            val loginGeneration = client.sessionGeneration(provider.id)
            if (current.id != provider.id || client.sessionGeneration(provider.id) != loginGeneration) return
            val cached = withContext(Dispatchers.IO) { client.cached(provider.id) } ?: return
            if (client.sessionGeneration(provider.id) != loginGeneration) return
            tokenState.readGeneration.value += 1
            tokens = cached
            onCommit(current.copy(baseUrl = GROK_CLI_PROXY_BASE_URL, useResponseApi = false, chatCompletionsPath = "/chat/completions"))
            toaster.show(
                context.getString(
                    R.string.setting_provider_page_grok_oauth_signed_in_toast,
                    result.email ?: context.getString(R.string.setting_provider_page_grok_oauth_account),
                ),
                type = ToastType.Success,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (e: Exception) {
            toaster.show(
                context.getString(
                    R.string.setting_provider_page_grok_oauth_login_failed,
                    (e.message ?: e).toString(),
                ),
                type = ToastType.Error,
            )
        } finally {
            busy = false
        }
    }

    val latestAutoStart by rememberUpdatedState(autoStartOAuth)
    val latestOnAutoStartConsumed by rememberUpdatedState(onAutoStartConsumed)
    LaunchedEffect(provider.id, tokenState.loaded.value) {
        if (latestAutoStart && tokenState.loaded.value && tokens == null && !busy) {
            latestOnAutoStartConsumed()
            login()
        }
    }
    ProviderLabeledField("API Base URL") {
        ProviderTextField(value = GROK_CLI_PROXY_BASE_URL, onValueChange = {}, mono = true, readOnly = true)
    }
    ProviderMonoNote(
        if (busy) stringResource(R.string.setting_provider_page_grok_oauth_waiting_note)
        else tokens?.let {
            it.email?.let { email ->
                stringResource(R.string.setting_provider_page_grok_oauth_signed_in_as, email)
            } ?: stringResource(R.string.setting_provider_page_grok_oauth_signed_in)
        } ?: stringResource(R.string.setting_provider_page_grok_oauth_not_signed_in),
    )
    ProviderCommandButton(
        text = when {
            busy -> stringResource(R.string.setting_provider_page_grok_oauth_waiting)
            tokens == null -> stringResource(R.string.setting_provider_page_grok_oauth_login)
            else -> stringResource(R.string.setting_provider_page_grok_oauth_relogin)
        },
        accent = tokens == null,
        enabled = tokenState.loaded.value && !busy,
        onClick = { scope.launch { login() } },
        modifier = Modifier.fillMaxWidth(),
    )
    if (tokens != null) {
        ProviderCommandButton(
            text = stringResource(R.string.setting_provider_page_grok_oauth_logout),
            onClick = {
                tokenState.readGeneration.value += 1
                client.logout(provider.id)
                tokens = null
                onCommit(
                    provider.switchOpenAIAuthMode(OpenAIAuthMode.API_KEY, store.getBackup(provider.id)),
                )
                store.clearBackup(provider.id)
                toaster.show(context.getString(R.string.setting_provider_page_grok_oauth_signed_out), type = ToastType.Success)
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun ProviderMonoNote(text: String) {
    val t = LocalAmberTokens.current
    val type = LocalAmberType.current
    Text(
        text = text,
        style = type.meta.copy(fontSize = 11.sp),
        color = t.ink3,
    )
}

@Composable
private fun rememberGoogleServiceAccountImport(
    provider: ProviderSetting.Google,
    onEdit: (ProviderSetting.Google) -> Unit,
): ManagedActivityResultLauncher<Array<String>, Uri?> {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    return rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val content = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.readText()
                ?: return@rememberLauncherForActivityResult
            val json = Json.parseToJsonElement(content).jsonObject
            onEdit(
                provider.copy(
                    projectId = json["project_id"]?.jsonPrimitive?.contentOrNull?.ifEmpty { null }
                        ?: provider.projectId,
                    serviceAccountEmail = json["client_email"]?.jsonPrimitive?.contentOrNull?.ifEmpty { null }
                        ?: provider.serviceAccountEmail,
                    privateKey = json["private_key"]?.jsonPrimitive?.contentOrNull?.ifEmpty { null }
                        ?: provider.privateKey,
                )
            )
            toaster.show("Service account imported", type = ToastType.Success)
        } catch (e: Exception) {
            toaster.show("Failed to import: ${e.message}", type = ToastType.Error)
        }
    }
}

internal fun ProviderSetting.OpenAI.isGrokProvider(): Boolean =
    name.equals("xAI", ignoreCase = true) || baseUrl.contains("api.x.ai", ignoreCase = true) ||
        baseUrl.contains("cli-chat-proxy.grok.com", ignoreCase = true)

internal fun ProviderSetting.OpenAI.switchOpenAIAuthMode(
    mode: OpenAIAuthMode,
    grokBaseUrl: String? = null,
): ProviderSetting.OpenAI {
    val pinned = mode.fixedBaseUrl()
    if (mode == OpenAIAuthMode.GROK_OAUTH) {
        // Keep the API-key endpoint until OAuth succeeds. GrokOAuthConsole
        // snapshots this value before committing the proxy endpoint.
        return copy(authMode = mode, useResponseApi = false, chatCompletionsPath = "/chat/completions")
    }
    return when (mode) {
        OpenAIAuthMode.CODEX_OAUTH -> copy(
            authMode = OpenAIAuthMode.CODEX_OAUTH,
            baseUrl = pinned ?: baseUrl,
            useResponseApi = true,
            name = if (name == "OpenAI") "OpenAI Codex OAuth" else name,
        )
        OpenAIAuthMode.ZHIPU_CODING_PLAN,
        OpenAIAuthMode.KIMI_CODING_PLAN,
        OpenAIAuthMode.MIMO_CODING_PLAN,
        OpenAIAuthMode.MINIMAX_TOKEN_PLAN -> copy(authMode = mode, baseUrl = pinned ?: baseUrl)
        OpenAIAuthMode.API_KEY -> {
            if (authMode == OpenAIAuthMode.GROK_OAUTH) {
                return copy(
                    authMode = OpenAIAuthMode.API_KEY,
                    baseUrl = grokBaseUrl ?: "https://api.x.ai/v1",
                    // Match logout: the xAI API-key preset uses Responses.
                    useResponseApi = true,
                )
            }
            val leavingManagedMode = authMode != OpenAIAuthMode.API_KEY
            val restoredBaseUrl = if (leavingManagedMode) {
                (resetBaseUrlToDefault() as ProviderSetting.OpenAI).baseUrl
            } else {
                baseUrl
            }
            copy(
                authMode = OpenAIAuthMode.API_KEY,
                baseUrl = restoredBaseUrl,
                useResponseApi = if (leavingManagedMode) false else useResponseApi,
            )
        }
    }
}

private fun ProviderSetting.Google.switchGoogleAuthMode(mode: GoogleAuthMode): ProviderSetting.Google {
    val pinned = mode.fixedBaseUrl()
    return when (mode) {
        GoogleAuthMode.GEMINI_CODE_ASSIST_OAUTH,
        GoogleAuthMode.ANTIGRAVITY_OAUTH -> {
            // Entering a managed mode must not destroy a working API-key /
            // service-account configuration before login actually succeeds:
            // cancel or logout keeps the original fields intact. The OAuth
            // console clears them in its onCommit (post-login) path instead.
            copy(
                authMode = mode,
                baseUrl = pinned ?: baseUrl,
            )
        }
        GoogleAuthMode.API_KEY -> {
            val knownPinnedUrls = setOfNotNull(
                GoogleAuthMode.GEMINI_CODE_ASSIST_OAUTH.fixedBaseUrl(),
                GoogleAuthMode.ANTIGRAVITY_OAUTH.fixedBaseUrl(),
            )
            val restoredBaseUrl = if (baseUrl in knownPinnedUrls) {
                (resetBaseUrlToDefault() as ProviderSetting.Google).baseUrl
            } else {
                baseUrl
            }
            copy(authMode = mode, baseUrl = restoredBaseUrl)
        }
    }
}

private fun OpenAIAuthMode.openAIAuthLabel(): String = when (this) {
    OpenAIAuthMode.API_KEY -> "API Key"
    OpenAIAuthMode.CODEX_OAUTH -> "OAuth"
    OpenAIAuthMode.GROK_OAUTH -> "Grok"
    OpenAIAuthMode.ZHIPU_CODING_PLAN,
    OpenAIAuthMode.KIMI_CODING_PLAN,
    OpenAIAuthMode.MIMO_CODING_PLAN,
    OpenAIAuthMode.MINIMAX_TOKEN_PLAN -> "Token Plan"
}
