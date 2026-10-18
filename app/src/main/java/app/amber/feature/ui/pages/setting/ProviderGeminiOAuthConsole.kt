package app.amber.feature.ui.pages.setting

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CancellationException
import app.amber.ai.provider.GoogleAuthMode
import app.amber.ai.provider.ProviderSetting
import app.amber.ai.provider.fixedBaseUrl
import app.amber.ai.provider.providers.google.GoogleGeminiAuthStore
import app.amber.ai.provider.providers.google.GoogleGeminiAuthTokens
import app.amber.ai.provider.providers.google.GoogleGeminiOAuthClient
import app.amber.ai.provider.providers.google.OBSOLETE_GEMINI_OAUTH_MODEL_IDS
import app.amber.ai.provider.providers.google.defaultGeminiOAuthModelList
import app.amber.agent.R
import app.amber.core.localization.OAuthDisplayLocalizer
import app.amber.feature.ui.context.LocalToaster
import app.amber.feature.ui.pages.setting.components.ProviderCommandButton
import app.amber.feature.ui.pages.setting.components.ProviderTextField

import com.dokar.sonner.ToastType
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

import app.amber.feature.ui.pages.setting.provider.ProviderLabeledField

@Composable
internal fun GeminiOAuthConsole(
    provider: ProviderSetting.Google,
    onCommit: (ProviderSetting.Google) -> Unit,
    autoStartOAuth: Boolean,
    onAutoStartConsumed: () -> Unit,
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val geminiAuthStore = koinInject<GoogleGeminiAuthStore>()
    val geminiOAuthClient = koinInject<GoogleGeminiOAuthClient>()
    var geminiTokens by remember(provider.id) {
        mutableStateOf<GoogleGeminiAuthTokens?>(geminiAuthStore.get(provider.id))
    }
    var geminiOAuthBusy by remember(provider.id) { mutableStateOf(false) }
    val latestProvider by rememberUpdatedState(provider)
    val latestOnCommit by rememberUpdatedState(onCommit)

    suspend fun runGeminiLogin() {
        if (geminiOAuthBusy) return
        val loginRequestKey = provider.modelListRequestKey()
        geminiOAuthBusy = true
        try {
            val tokens = geminiOAuthClient.authorize(
                context,
                provider.id,
                loopbackCopy = OAuthDisplayLocalizer.loopback(context),
            )
            val currentProvider = latestProvider
            if (!geminiOAuthResultIsCurrent(loginRequestKey, currentProvider)) {
                toaster.show(
                    context.getString(R.string.setting_provider_page_gemini_oauth_signed_in),
                    type = ToastType.Info,
                )
                return
            }
            geminiTokens = tokens
            val isAllObsolete = currentProvider.models.isNotEmpty() &&
                currentProvider.models.all { it.modelId in OBSOLETE_GEMINI_OAUTH_MODEL_IDS }
            val newSelection = if (currentProvider.models.isEmpty() || isAllObsolete) {
                defaultGeminiOAuthModelList()
            } else {
                currentProvider.models
            }
            latestOnCommit(
                currentProvider.copy(
                    models = newSelection,
                    name = if (currentProvider.name == "Google") "Gemini OAuth" else currentProvider.name,
                )
            )
            toaster.show(
                context.getString(
                    R.string.setting_provider_page_gemini_oauth_login_success,
                    tokens.email ?: context.getString(R.string.setting_provider_page_google_account),
                ),
                type = ToastType.Success,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (e: Exception) {
            toaster.show(
                context.getString(R.string.setting_provider_page_gemini_oauth_login_failed, e.message ?: e.toString()),
                type = ToastType.Error,
            )
        } finally {
            geminiOAuthBusy = false
        }
    }

    val latestAutoStart by rememberUpdatedState(autoStartOAuth)
    val latestOnAutoStartConsumed by rememberUpdatedState(onAutoStartConsumed)
    LaunchedEffect(provider.id) {
        if (
            latestAutoStart &&
            provider.authMode == GoogleAuthMode.GEMINI_CODE_ASSIST_OAUTH &&
            geminiTokens == null &&
            !geminiOAuthBusy
        ) {
            latestOnAutoStartConsumed()
            runGeminiLogin()
        }
    }

    ProviderLabeledField("API Base URL") {
        ProviderTextField(
            value = checkNotNull(GoogleAuthMode.GEMINI_CODE_ASSIST_OAUTH.fixedBaseUrl()),
            onValueChange = {},
            mono = true,
            readOnly = true,
        )
    }
    ProviderMonoNote(
        geminiTokens?.let { tokens ->
            tokens.email?.let { stringResource(R.string.setting_provider_page_gemini_oauth_signed_in_as, it) }
                ?: stringResource(R.string.setting_provider_page_gemini_oauth_signed_in)
        } ?: stringResource(R.string.setting_provider_page_gemini_oauth_not_signed_in)
    )
    ProviderCommandButton(
        text = if (geminiTokens != null) {
            stringResource(R.string.setting_provider_page_gemini_oauth_relogin)
        } else {
            stringResource(R.string.setting_provider_page_gemini_oauth_login)
        },
        accent = geminiTokens == null,
        onClick = { scope.launch { runGeminiLogin() } },
        modifier = Modifier.fillMaxWidth(),
    )
    if (geminiTokens != null) {
        ProviderCommandButton(
            text = stringResource(R.string.setting_provider_page_gemini_oauth_logout),
            onClick = {
                geminiOAuthClient.logout(provider.id)
                geminiTokens = null
                toaster.show(
                    context.getString(R.string.setting_provider_page_gemini_oauth_logged_out),
                    type = ToastType.Success,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
