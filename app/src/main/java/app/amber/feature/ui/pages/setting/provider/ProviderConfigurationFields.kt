package app.amber.feature.ui.pages.setting.provider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.amber.ai.provider.GoogleAuthMode
import app.amber.ai.provider.BalanceResultPath
import app.amber.ai.provider.OpenAIAuthMode
import app.amber.ai.provider.ProviderSetting
import app.amber.ai.provider.fixedBaseUrl
import app.amber.ai.provider.providers.openai.supportsResponsesResume
import app.amber.agent.R
import app.amber.feature.ui.components.ai.ProviderBalanceText
import app.amber.feature.ui.pages.setting.components.ProviderCard
import app.amber.feature.ui.pages.setting.components.ProviderCommandButton
import app.amber.feature.ui.pages.setting.components.ProviderSecretField
import app.amber.feature.ui.pages.setting.components.ProviderSectionLabel
import app.amber.feature.ui.pages.setting.components.ProviderSegOption
import app.amber.feature.ui.pages.setting.components.ProviderTextField

import app.amber.feature.ui.theme.LocalAmberTokens
import app.amber.feature.ui.theme.LocalAmberType

@Composable
internal fun ProviderProtocolSection(
    provider: ProviderSetting,
    onEdit: (ProviderSetting) -> Unit,
) {
    ProviderSectionLabel(stringResource(R.string.setting_provider_page_protocol))
    val options = ProviderSetting.Types.map { type ->
        ProviderSegOption(type, type.simpleName ?: "")
    }
    ProviderCard(modifier = Modifier.fillMaxWidth()) {
        ProviderConfigCapsule(
            options = options,
            selected = provider::class,
            onSelected = { type -> onEdit(provider.convertTo(type)) },
            modifier = Modifier.padding(10.dp),
        )
    }
}

@Composable
internal fun ClaudeAuthConsole(
    provider: ProviderSetting.Claude,
    onEdit: (ProviderSetting.Claude) -> Unit,
) {
    ProviderLabeledField("API Key") {
        ProviderSecretField(
            value = provider.apiKey,
            onValueChange = { onEdit(provider.copy(apiKey = it.trim())) },
            placeholder = "sk-ant-...",
        )
    }
}

@Composable
internal fun ProviderEndpointSection(
    provider: ProviderSetting,
    onEdit: (ProviderSetting) -> Unit,
    onImportGoogleServiceAccount: () -> Unit,
) {
    ProviderSectionLabel(stringResource(R.string.setting_provider_page_endpoint))
    ProviderCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ProviderLabeledField(stringResource(R.string.setting_provider_page_display_name)) {
                ProviderTextField(
                    value = provider.name,
                    onValueChange = { onEdit(provider.copyProvider(name = it.trim())) },
                    placeholder = "Provider",
                )
            }
            when (provider) {
                is ProviderSetting.OpenAI -> OpenAIEndpointFields(provider, onEdit)
                is ProviderSetting.Google -> GoogleEndpointFields(provider, onEdit, onImportGoogleServiceAccount)
                is ProviderSetting.Claude -> ClaudeEndpointFields(provider, onEdit)
            }
        }
    }
}

@Composable
private fun OpenAIEndpointFields(
    provider: ProviderSetting.OpenAI,
    onEdit: (ProviderSetting.OpenAI) -> Unit,
) {
    // Grok OAuth pins the CLI proxy endpoint exactly like Codex does.
    val fixed = provider.authMode == OpenAIAuthMode.CODEX_OAUTH ||
        provider.authMode == OpenAIAuthMode.GROK_OAUTH
    ProviderLabeledField("API Base URL") {
        ProviderTextField(
            value = if (fixed) checkNotNull(provider.authMode.fixedBaseUrl()) else provider.baseUrl,
            onValueChange = { onEdit(provider.copy(baseUrl = it.trim())) },
            mono = true,
            readOnly = fixed,
        )
    }
    if (!provider.authMode.isCodingPlan() && !fixed) {
        if (!provider.useResponseApi) {
            ProviderLabeledField("API Path") {
                ProviderTextField(
                    value = provider.chatCompletionsPath,
                    onValueChange = { onEdit(provider.copy(chatCompletionsPath = it.trim())) },
                    mono = true,
                    readOnly = provider.builtIn,
                )
            }
        }
        ProviderSwitchRow(
            title = stringResource(R.string.setting_provider_page_response_api),
            checked = provider.useResponseApi,
            onCheckedChange = { onEdit(provider.copy(useResponseApi = it)) },
        )
        // P6-01: server-side resume is only offered on the strictly matching
        // official configuration (api.openai.com + API key + Responses API);
        // custom endpoints, Codex OAuth and third-party providers never show
        // the switch (plan §P6-01 不适用清单).
        if (provider.supportsResponsesResume()) {
            ProviderSwitchRow(
                title = stringResource(R.string.setting_provider_page_responses_resume),
                checked = provider.enableResponsesResume,
                onCheckedChange = { onEdit(provider.copy(enableResponsesResume = it)) },
            )
        }
    }
}

@Composable
private fun GoogleEndpointFields(
    provider: ProviderSetting.Google,
    onEdit: (ProviderSetting.Google) -> Unit,
    onImportServiceAccount: () -> Unit,
) {
    if (provider.authMode == GoogleAuthMode.GEMINI_CODE_ASSIST_OAUTH || provider.authMode == GoogleAuthMode.ANTIGRAVITY_OAUTH) {
        ProviderLabeledField("API Base URL") {
            ProviderTextField(
                value = checkNotNull(provider.authMode.fixedBaseUrl()),
                onValueChange = {},
                mono = true,
                readOnly = true,
            )
        }
        return
    }

    if (!provider.vertexAI) {
        ProviderLabeledField("API Base URL") {
            ProviderTextField(
                value = provider.baseUrl,
                onValueChange = { onEdit(provider.copy(baseUrl = it.trim())) },
                mono = true,
            )
        }
    } else if (provider.useServiceAccount) {
        ProviderCommandButton(
            text = stringResource(R.string.setting_provider_page_import_service_account_json),
            onClick = onImportServiceAccount,
        )
        ProviderLabeledField("Service Account Email") {
            ProviderTextField(
                value = provider.serviceAccountEmail,
                onValueChange = { onEdit(provider.copy(serviceAccountEmail = it.trim())) },
                mono = true,
            )
        }
        ProviderLabeledField("Private Key") {
            ProviderSecretField(
                value = provider.privateKey,
                onValueChange = { onEdit(provider.copy(privateKey = it.trim())) },
            )
        }
        ProviderLabeledField("Location") {
            ProviderTextField(
                value = provider.location,
                onValueChange = { onEdit(provider.copy(location = it.trim())) },
                mono = true,
            )
        }
        ProviderLabeledField("Project ID") {
            ProviderTextField(
                value = provider.projectId,
                onValueChange = { onEdit(provider.copy(projectId = it.trim())) },
                mono = true,
            )
        }
    }
}

@Composable
private fun ClaudeEndpointFields(
    provider: ProviderSetting.Claude,
    onEdit: (ProviderSetting.Claude) -> Unit,
) {
    ProviderLabeledField("API Base URL") {
        ProviderTextField(
            value = provider.baseUrl,
            onValueChange = { onEdit(provider.copy(baseUrl = it.trim())) },
            mono = true,
        )
    }
    ProviderSwitchRow(
        title = stringResource(R.string.setting_provider_page_claude_prompt_caching),
        checked = provider.promptCaching,
        onCheckedChange = { onEdit(provider.copy(promptCaching = it)) },
    )
}

@Composable
internal fun ProviderAccountSection(
    provider: ProviderSetting.OpenAI,
    onEdit: (ProviderSetting.OpenAI) -> Unit,
) {
    val t = LocalAmberTokens.current
    val type = LocalAmberType.current
    ProviderSectionLabel(stringResource(R.string.setting_provider_page_account))
    ProviderCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ProviderSwitchRow(
                title = stringResource(R.string.setting_provider_page_get_account_balance),
                checked = provider.balanceOption.enabled,
                onCheckedChange = {
                    onEdit(provider.copy(balanceOption = provider.balanceOption.copy(enabled = it)))
                },
            )
            if (provider.balanceOption.enabled) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    ProviderBalanceText(
                        providerSetting = provider,
                        style = type.meta.copy(color = t.signal),
                    )
                }
                ProviderLabeledField("API Path") {
                    ProviderTextField(
                        value = provider.balanceOption.apiPath,
                        onValueChange = {
                            onEdit(provider.copy(balanceOption = provider.balanceOption.copy(apiPath = it.trim())))
                        },
                        mono = true,
                    )
                }
                ProviderLabeledField("Result Path") {
                    ProviderTextField(
                        value = provider.balanceOption.resultPath,
                        onValueChange = {
                            onEdit(provider.copy(balanceOption = provider.balanceOption.copy(resultPath = it.trim())))
                        },
                        isError = !BalanceResultPath.isValid(provider.balanceOption.resultPath),
                        mono = true,
                    )
                }
            }
        }
    }
}
