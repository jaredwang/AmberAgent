package app.amber.feature.ui.pages.sessionhome

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.amber.agent.R
import app.amber.core.model.Conversation
import app.amber.feature.ui.components.ds.AmberContinuousShape
import app.amber.feature.ui.components.ds.AmberDepthStyle
import app.amber.feature.ui.components.ds.amberPressHighlight
import app.amber.feature.ui.components.ds.amberRim
import app.amber.feature.ui.components.ds.amberShadow
import app.amber.feature.ui.theme.JetBrainsMonoFamily
import app.amber.feature.ui.theme.LocalAmberTokens
import app.amber.feature.ui.theme.LocalThemePack
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import androidx.compose.material3.Text
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Pin

/**
 * 会话行：标题 + 最后消息预览 +（消息数 / 时间）。
 * 左滑（EndToStart）删除，右滑（StartToEnd）置顶/取消置顶——M3 SwipeToDismissBox 双向，
 * 与 HistoryPage 的单向删除同款机制。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeSessionRow(
    conversation: Conversation,
    tileColor: Color,
    isLastVisited: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
) {
    val tokens = LocalAmberTokens.current
    val cardRadius = LocalThemePack.current?.design?.components?.cardRadius?.toFloat()?.dp ?: 22.dp
    val newMessageLabel = stringResource(R.string.chat_page_new_message)
    val deleteLabel = stringResource(R.string.delete)
    val pinLabel = stringResource(R.string.history_page_pin)
    val unpinLabel = stringResource(R.string.history_page_unpin)
    val rowInteraction = remember { MutableInteractionSource() }
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = SwipeToDismissBoxDefaults.positionalThreshold,
        // 置顶是「动作后行仍保留」的方向：在 confirmValueChange 里执行动作并返回
        // false，状态永远不进入 StartToEnd，松手必弹回 Settled。旧版是进入
        // StartToEnd 后 LaunchedEffect 里 onTogglePin()+reset()，但置顶引起的列表
        // 重排会和 reset 赛跑，行会卡在滑出位置不回弹。删除方向返回 true 交给
        // 下面的 LaunchedEffect（项会被移除，无需回弹）。
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd) {
                onTogglePin()
                false
            } else {
                true
            }
        },
    )

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onDelete()
        }
    }

    // 与顶部入口卡同一套连续曲率圆角，列表整体读作一张卡。
    val rowShape = remember(isFirst, isLast, cardRadius) {
        AmberContinuousShape(
            topStart = if (isFirst) cardRadius else 0.dp,
            topEnd = if (isFirst) cardRadius else 0.dp,
            bottomEnd = if (isLast) cardRadius else 0.dp,
            bottomStart = if (isLast) cardRadius else 0.dp,
        )
    }
    val rowSurface = if (isLastVisited) {
        tokens.accent.copy(alpha = if (tokens.isDark) 0.16f else 0.08f)
            .compositeOver(tokens.surface)
    } else {
        tokens.surface
    }
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            // 左滑露出右侧「删除」（accent 底）；右滑露出左侧「置顶」（surface2 底）
            val isDelete = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isDelete) tokens.accent else tokens.surface2)
                    .padding(horizontal = 20.dp),
                contentAlignment = if (isDelete) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = if (isDelete) Lucide.Trash2 else Lucide.Pin,
                        contentDescription = if (isDelete) {
                            deleteLabel
                        } else {
                            if (conversation.isPinned) unpinLabel else pinLabel
                        },
                        modifier = Modifier.size(17.dp),
                        tint = if (isDelete) tokens.accentInk else tokens.accent,
                    )
                    Text(
                        text = when {
                            isDelete -> deleteLabel
                            conversation.isPinned -> unpinLabel
                            else -> pinLabel
                        },
                        fontFamily = homeChromeFontFamily(JetBrainsMonoFamily),
                        fontSize = 10.sp,
                        letterSpacing = 0.4.sp,
                        color = if (isDelete) tokens.accentInk else tokens.accent,
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .amberShadow(
                rowShape,
                AmberDepthStyle.Card,
                drawTop = isFirst,
                drawBottom = isLast,
                opaqueContent = rowSurface.alpha >= 1f,
            )
            .clip(rowShape)
            .amberRim(rowShape, AmberDepthStyle.Card, drawTop = isFirst, drawBottom = isLast),
    ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(rowSurface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .amberPressHighlight(rowInteraction, rowShape, scaleOnPress = false)
                        .background(rowSurface)
                        .clickable(
                            interactionSource = rowInteraction,
                            indication = null,
                            onClick = onOpen,
                        )
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(tileColor.copy(alpha = 0.13f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        HomeSessionGlyph(
                            title = conversation.title,
                            modifier = Modifier.size(18.dp),
                            tint = tileColor,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = conversation.title.ifBlank { newMessageLabel },
                            fontSize = 13.5.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.2).sp,
                            color = tokens.ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = sessionTimeLabel(conversation.updateAt),
                                fontFamily = homeChromeFontFamily(JetBrainsMonoFamily),
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                fontWeight = FontWeight.Normal,
                                color = tokens.ink3,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "·",
                                fontFamily = homeChromeFontFamily(JetBrainsMonoFamily),
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                color = tokens.ink4,
                            )
                            SessionCountBadge(count = conversation.messageCount)
                        }
                    }
                    if (conversation.isPinned) {
                        Spacer(Modifier.width(12.dp))
                        Icon(
                            imageVector = Lucide.Pin,
                            contentDescription = pinLabel,
                            modifier = Modifier.size(14.dp),
                            tint = tileColor,
                        )
                    }
                }
                if (!isLast) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 60.dp, end = 14.dp)
                            .height(0.5.dp)
                            .background(tokens.line.copy(alpha = 0.46f)),
                    )
                }
        }
    }
}

/** 消息数与时间保持相同的字号、字重和灰度。 */
@Composable
private fun SessionCountBadge(count: Int) {
    val tokens = LocalAmberTokens.current
    Text(
        text = stringResource(R.string.session_home_message_count, if (count > 99) "99+" else count.toString()),
        fontFamily = homeChromeFontFamily(JetBrainsMonoFamily),
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Normal,
        color = tokens.ink3,
        maxLines = 1,
    )
}

internal fun homeConversationTileColor(
    tokens: app.amber.feature.ui.theme.AmberTokens,
    isLastVisited: Boolean,
): Color {
    return if (isLastVisited) tokens.accent else tokens.ink3
}

/** 相对时间：今天 → HH:mm，昨天 → 昨天，更早 → M月d日（跨年带年份）。 */
@Composable
private fun sessionTimeLabel(instant: Instant): String {
    val zone = ZoneId.systemDefault()
    val dateTime = instant.atZone(zone)
    val today = LocalDate.now(zone)
    val date = dateTime.toLocalDate()
    return when {
        date == today -> String.format("%02d:%02d", dateTime.hour, dateTime.minute)
        date == today.minusDays(1) -> stringResource(R.string.chat_page_yesterday)
        date.year == today.year -> stringResource(
            R.string.session_home_date_month_day,
            date.monthValue,
            date.dayOfMonth,
        )
        else -> stringResource(
            R.string.session_home_date_year_month_day,
            date.year,
            date.monthValue,
            date.dayOfMonth,
        )
    }
}
