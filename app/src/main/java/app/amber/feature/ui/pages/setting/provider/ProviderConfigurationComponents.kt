package app.amber.feature.ui.pages.setting.provider

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.amber.ai.provider.ProviderSetting
import app.amber.ai.provider.hasUsableAuth
import app.amber.agent.R
import app.amber.feature.ui.components.ds.pressable
import app.amber.feature.ui.components.ds.BtnInk
import app.amber.feature.ui.pages.setting.components.ProviderCard
import app.amber.feature.ui.pages.setting.components.ProviderCommandButton
import app.amber.feature.ui.pages.setting.components.ProviderHairline
import app.amber.feature.ui.pages.setting.components.ProviderLiveDot
import app.amber.feature.ui.pages.setting.components.ProviderMonogram
import app.amber.feature.ui.pages.setting.components.ProviderSegOption
import app.amber.feature.ui.pages.setting.components.ProviderToggle

import app.amber.feature.ui.pages.setting.components.providerAuthLabel
import app.amber.feature.ui.pages.setting.components.toProviderMonogram
import app.amber.feature.ui.theme.LocalAmberTokens
import app.amber.feature.ui.theme.LocalAmberType

@Composable
internal fun ProviderDeleteDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val t = LocalAmberTokens.current
    val type = LocalAmberType.current
    Dialog(onDismissRequest = onDismiss) {
        ProviderCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = stringResource(R.string.confirm_delete),
                    style = type.body.copy(fontWeight = FontWeight.Bold),
                    color = t.ink,
                )
                Text(
                    text = stringResource(R.string.setting_provider_page_delete_dialog_text),
                    style = type.secondary,
                    color = t.ink3,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    ProviderCommandButton(
                        text = stringResource(R.string.cancel),
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    ProviderCommandButton(
                        text = stringResource(R.string.delete),
                        onClick = onConfirm,
                        accent = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/* v5 ledger: identity row — monogram + name/meta + enable toggle right, hairline below */
@Composable
internal fun ProviderConsoleIdentity(
    provider: ProviderSetting,
    onEdit: (ProviderSetting) -> Unit,
    oauthUsable: Boolean? = null,
) {
    val t = LocalAmberTokens.current
    val type = LocalAmberType.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        ProviderMonogram(
            text = provider.name.toProviderMonogram(),
            size = 40.dp,
            enabled = provider.enabled,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = provider.name,
                style = type.sessionTitle,
                color = t.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                itemVerticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val connected = provider.hasUsableAuth(oauthUsable)
                ProviderConfigStatusPill(
                    connected = connected,
                    text = when {
                        !provider.enabled -> stringResource(R.string.setting_provider_page_disabled)
                        connected -> stringResource(R.string.setting_provider_page_connected)
                        else -> stringResource(R.string.setting_provider_page_not_connected)
                    },
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("·", style = type.meta, color = t.ink4)
                    ProviderConfigAuthBadge(provider.providerAuthLabel())
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("·", style = type.meta, color = t.ink4)
                    Text(
                        text = stringResource(
                            R.string.setting_provider_page_model_count,
                            provider.models.size,
                        ),
                        style = type.meta.copy(fontSize = 10.5.sp),
                        color = t.ink2,
                        maxLines = 1,
                    )
                }
            }
        }
        ProviderToggle(
            checked = provider.enabled,
            onCheckedChange = { onEdit(provider.copyProvider(enabled = it)) },
        )
    }
    ProviderHairline()
}

/** Provider-config fields follow the reference row pattern: a readable title above a 44dp well. */
@Composable
internal fun ProviderLabeledField(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val t = LocalAmberTokens.current
    val type = LocalAmberType.current
    Column(
        modifier = modifier.padding(top = 12.dp),
    ) {
        Text(
            text = label,
            style = type.body.copy(fontWeight = FontWeight.SemiBold),
            color = t.ink,
            modifier = Modifier.padding(start = 2.dp, bottom = 7.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
internal fun <T> ProviderConfigCapsule(
    options: List<ProviderSegOption<T>>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalAmberTokens.current
    val type = LocalAmberType.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(CircleShape)
                .background(t.surface2)
                .border(1.dp, t.line.copy(alpha = 0.52f), CircleShape)
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            options.forEach { option ->
                val isSelected = option.value == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(if (isSelected) t.accent else Color.Transparent)
                        .pressable(onClick = { onSelected(option.value) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option.label,
                        style = type.secondary.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                        color = if (isSelected) t.accentInk else t.ink3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderConfigStatusPill(
    connected: Boolean,
    text: String,
) {
    val t = LocalAmberTokens.current
    val type = LocalAmberType.current
    val statusColor = if (connected) t.signal else t.ink3
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (connected) t.signal.copy(alpha = 0.14f) else t.surface2)
            .border(1.dp, if (connected) t.signal.copy(alpha = 0.26f) else t.line.copy(alpha = 0.44f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ProviderLiveDot(enabled = connected, size = 7.dp)
            Text(
                text = text,
                style = type.meta.copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold),
                color = statusColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ProviderConfigAuthBadge(text: String) {
    val t = LocalAmberTokens.current
    val type = LocalAmberType.current
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(t.surface2)
            .border(1.dp, t.line.copy(alpha = 0.44f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = type.meta.copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold),
            color = if (text == "—") t.ink4 else t.ink2,
            maxLines = 1,
        )
    }
}

@Composable
internal fun ProviderConsoleActions(
    provider: ProviderSetting,
    onDelete: () -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ProviderConfigQuietAction(
            text = stringResource(R.string.setting_model_page_reset_to_default),
            enabled = !provider.isUsingDefaultBaseUrl(),
            onClick = onReset,
        )
        ProviderConfigQuietAction(
            text = stringResource(R.string.delete),
            enabled = true,
            onClick = onDelete,
            danger = true,
        )
        BtnInk(
            text = stringResource(R.string.setting_provider_page_save),
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
    }
}

@Composable
private fun ProviderConfigQuietAction(
    text: String,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val t = LocalAmberTokens.current
    val type = LocalAmberType.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(if (enabled) Modifier.pressable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = type.secondary.copy(fontWeight = FontWeight.SemiBold),
            color = when {
                !enabled -> t.ink4
                danger -> MaterialTheme.colorScheme.error
                else -> t.ink2
            },
        )
    }
}

@Composable
internal fun ProviderSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val t = LocalAmberTokens.current
    val type = LocalAmberType.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = t.accent,
                modifier = Modifier.size(17.dp),
            )
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = type.body,
            color = t.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        ProviderToggle(checked = checked, onCheckedChange = onCheckedChange)
    }
}
