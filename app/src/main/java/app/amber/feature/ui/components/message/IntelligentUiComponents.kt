package app.amber.feature.ui.components.message

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.amber.core.ai.generative.IntelligentUiAlignment
import app.amber.core.ai.generative.IntelligentUiCardState
import app.amber.core.ai.generative.IntelligentUiComponentKind
import app.amber.core.ai.generative.IntelligentUiComponentNode
import app.amber.core.ai.generative.IntelligentUiDensity
import app.amber.core.ai.generative.IntelligentUiDocument
import app.amber.core.ai.generative.IntelligentUiEmphasis
import app.amber.core.ai.generative.IntelligentUiStyle
import app.amber.core.ai.generative.IntelligentUiTone
import app.amber.feature.ui.pages.chat.LocalChatTheme
import app.amber.feature.ui.theme.AmberMono
import app.amber.feature.ui.theme.AmberSans
import app.amber.feature.ui.theme.AmberTokens
import app.amber.feature.ui.theme.LocalAmberTokens
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MapPin
import kotlin.math.max
import kotlin.math.roundToInt

private val PositiveToneColor = Color(0xFF1F8A5B)
private val WarningToneColor = Color(0xFFC2781E)
private val DangerToneColor = Color(0xFFC93B2B)
private val InfoToneColor = Color(0xFF2B6CB0)

/**
 * Recursive dispatcher that renders all 80 native `task-ui` component kinds in Jetpack Compose.
 */
@Composable
internal fun IntelligentUiComponentView(
    node: IntelligentUiComponentNode,
    document: IntelligentUiDocument,
    state: IntelligentUiCardState,
    onActionPrompt: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    val chatTheme = LocalChatTheme.current
    val accent = chatTheme.accent.takeIf { it.isSpecified } ?: tokens.accent

    when (node.kind) {
        // 1. Layout (10)
        IntelligentUiComponentKind.SECTION -> LayoutSectionView(node, document, state, onActionPrompt, modifier)
        IntelligentUiComponentKind.ROW -> LayoutRowView(node, document, state, onActionPrompt, modifier)
        IntelligentUiComponentKind.COLUMN -> LayoutColumnView(node, document, state, onActionPrompt, modifier)
        IntelligentUiComponentKind.GRID -> LayoutGridView(node, document, state, onActionPrompt, modifier)
        IntelligentUiComponentKind.CARD -> LayoutCardView(node, document, state, onActionPrompt, modifier)
        IntelligentUiComponentKind.SPLIT -> LayoutSplitView(node, document, state, onActionPrompt, modifier)
        IntelligentUiComponentKind.TABS -> LayoutTabsView(node, document, state, onActionPrompt, modifier)
        IntelligentUiComponentKind.ACCORDION,
        IntelligentUiComponentKind.DISCLOSURE -> LayoutDisclosureView(node, document, state, onActionPrompt, modifier)
        IntelligentUiComponentKind.DIVIDER -> HorizontalDivider(
            modifier = modifier.padding(vertical = 4.dp),
            thickness = 0.5.dp,
            color = tokens.line2,
        )

        // 2. Text (12)
        IntelligentUiComponentKind.HEADING -> TextHeadingView(node, isSubheading = false, modifier = modifier)
        IntelligentUiComponentKind.SUBHEADING -> TextHeadingView(node, isSubheading = true, modifier = modifier)
        IntelligentUiComponentKind.TEXT -> TextBodyView(node, isCaption = false, modifier = modifier)
        IntelligentUiComponentKind.CAPTION -> TextBodyView(node, isCaption = true, modifier = modifier)
        IntelligentUiComponentKind.EYEBROW -> TextEyebrowView(node, accent, modifier)
        IntelligentUiComponentKind.CALLOUT -> TextCalloutView(node, isQuote = false, accent = accent, modifier = modifier)
        IntelligentUiComponentKind.QUOTE -> TextCalloutView(node, isQuote = true, accent = accent, modifier = modifier)
        IntelligentUiComponentKind.CODE_BLOCK,
        IntelligentUiComponentKind.CODE_PREVIEW -> CodeBlockNodeView(node, modifier)
        IntelligentUiComponentKind.BULLET_LIST -> ListItemsView(node, numbered = false, modifier = modifier)
        IntelligentUiComponentKind.NUMBERED_LIST -> ListItemsView(node, numbered = true, modifier = modifier)
        IntelligentUiComponentKind.KEY_VALUE -> KeyValueSingleView(node, modifier)
        IntelligentUiComponentKind.DEFINITION_LIST -> DefinitionListView(node, modifier)

        // 3. Status (10)
        IntelligentUiComponentKind.BADGE,
        IntelligentUiComponentKind.TAG,
        IntelligentUiComponentKind.PILL -> StatusPillNodeView(node, accent, modifier)
        IntelligentUiComponentKind.STATUS_DOT -> StatusDotNodeView(node, accent, modifier)
        IntelligentUiComponentKind.ALERT,
        IntelligentUiComponentKind.BANNER -> StatusAlertBannerView(node, accent, modifier)
        IntelligentUiComponentKind.EMPTY_STATE,
        IntelligentUiComponentKind.SKELETON_NOTE -> StatusEmptyOrSkeletonView(node, modifier)
        IntelligentUiComponentKind.TIMELINE_ITEM,
        IntelligentUiComponentKind.STEP_ITEM -> TimelineStepItemView(node, accent, modifier)

        // 4. Input (14)
        IntelligentUiComponentKind.TEXT_FIELD -> InputTextFieldNodeView(node, state, multiline = false, numeric = false, modifier = modifier)
        IntelligentUiComponentKind.TEXT_AREA -> InputTextFieldNodeView(node, state, multiline = true, numeric = false, modifier = modifier)
        IntelligentUiComponentKind.NUMBER_FIELD -> InputTextFieldNodeView(node, state, multiline = false, numeric = true, modifier = modifier)
        IntelligentUiComponentKind.DATE_FIELD -> InputTextFieldNodeView(node, state, multiline = false, numeric = false, isDate = true, modifier = modifier)
        IntelligentUiComponentKind.SLIDER -> InputSliderNodeView(node, state, accent, modifier)
        IntelligentUiComponentKind.STEPPER -> InputStepperNodeView(node, state, accent, modifier)
        IntelligentUiComponentKind.RATING -> InputRatingNodeView(node, state, accent, modifier)
        IntelligentUiComponentKind.TOGGLE -> InputToggleCheckboxView(node, state, useSwitch = true, accent = accent, modifier = modifier)
        IntelligentUiComponentKind.CHECKBOX,
        IntelligentUiComponentKind.CHECKLIST_ITEM -> InputToggleCheckboxView(node, state, useSwitch = false, accent = accent, modifier = modifier)
        IntelligentUiComponentKind.RADIO_GROUP,
        IntelligentUiComponentKind.SELECT -> InputSingleChoiceView(node, state, segmented = false, accent = accent, modifier = modifier)
        IntelligentUiComponentKind.SEGMENTED_CONTROL,
        IntelligentUiComponentKind.FILTER_BAR -> InputSingleChoiceView(node, state, segmented = true, accent = accent, modifier = modifier)
        IntelligentUiComponentKind.MULTI_SELECT,
        IntelligentUiComponentKind.CHIP_GROUP -> InputMultiChoiceView(node, state, accent, modifier)

        // 5. Data (10)
        IntelligentUiComponentKind.METRIC -> DataMetricNodeView(node, document, state, accent, modifier)
        IntelligentUiComponentKind.METRIC_GRID -> DataMetricGridView(node, document, state, onActionPrompt, accent, modifier)
        IntelligentUiComponentKind.PROGRESS -> DataProgressNodeView(node, document, state, accent, modifier)
        IntelligentUiComponentKind.STEP_PROGRESS -> DataStepProgressNodeView(node, document, state, accent, modifier)
        IntelligentUiComponentKind.COMPARISON_ROW -> DataComparisonRowView(node, modifier)
        IntelligentUiComponentKind.PROS_CONS -> DataProsConsView(node, modifier)
        IntelligentUiComponentKind.TABLE -> DataTableView(node, modifier)
        IntelligentUiComponentKind.RANKING_LIST -> DataRankingListView(node, accent, modifier)
        IntelligentUiComponentKind.BAR_CHART -> DataBarChartView(node, document, state, accent, modifier)
        IntelligentUiComponentKind.LINE_CHART -> DataLineChartView(node, document, state, accent, modifier)

        // 6. Media (8) — CODE_PREVIEW handled above with CODE_BLOCK
        IntelligentUiComponentKind.ICON_LABEL -> MediaIconLabelView(node, accent, modifier)
        IntelligentUiComponentKind.AVATAR_ITEM -> MediaAvatarItemView(node, accent, modifier)
        IntelligentUiComponentKind.IMAGE_CARD -> MediaImageCardView(node, modifier)
        IntelligentUiComponentKind.GALLERY_STRIP -> MediaGalleryStripView(node, modifier)
        IntelligentUiComponentKind.LINK_CARD -> MediaLinkOrFileCardView(node, isFile = false, accent = accent, modifier = modifier)
        IntelligentUiComponentKind.FILE_CARD -> MediaLinkOrFileCardView(node, isFile = true, accent = accent, modifier = modifier)
        IntelligentUiComponentKind.MAP_PIN -> MediaMapPinView(node, accent, modifier)

        // 7. Task Semantic (8) — CHECKLIST_ITEM handled above
        IntelligentUiComponentKind.OPTION_CARD -> SemanticOptionCardView(node, state, accent, modifier)
        IntelligentUiComponentKind.RECOMMENDATION_HERO -> SemanticRecommendationHeroView(node, accent, modifier)
        IntelligentUiComponentKind.CONSTRAINT_ITEM -> SemanticConstraintOrRiskView(node, isRisk = false, modifier = modifier)
        IntelligentUiComponentKind.RISK_ITEM -> SemanticConstraintOrRiskView(node, isRisk = true, modifier = modifier)
        IntelligentUiComponentKind.FLASHCARD -> IntelligentUiSingleFlashcardNode(node = node, state = state, modifier = modifier)
        IntelligentUiComponentKind.QUIZ_ITEM -> IntelligentUiQuizItemNode(node = node, indexLabel = node.badge, state = state, modifier = modifier)
        IntelligentUiComponentKind.SCORE_SUMMARY -> IntelligentUiScoreSummarySection(
            flashcards = document.flashcardNodes(),
            quizNodes = document.quizNodes(),
            state = state,
            modifier = modifier,
        )

        // 8. Action & Container (8) — FILTER_BAR handled above
        IntelligentUiComponentKind.SEARCH_FILTER -> InputTextFieldNodeView(node, state, multiline = false, numeric = false, modifier = modifier)
        IntelligentUiComponentKind.ACTION_BUTTON -> ActionButtonNodeView(node, document, state, onActionPrompt, accent, modifier)
        IntelligentUiComponentKind.ACTION_BAR,
        IntelligentUiComponentKind.CONFIRM_BAR -> ActionBarNodeView(node, document, state, onActionPrompt, accent, modifier)
        IntelligentUiComponentKind.FOOTNOTE -> TextBodyView(node, isCaption = true, modifier = modifier)
        IntelligentUiComponentKind.SUMMARY_BOX,
        IntelligentUiComponentKind.CTA_CARD -> SummaryOrCtaCardView(node, document, state, onActionPrompt, accent, modifier)
    }
}

@Composable
private fun LayoutSectionView(
    node: IntelligentUiComponentNode,
    document: IntelligentUiDocument,
    state: IntelligentUiCardState,
    onActionPrompt: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    val spacing = node.style.resolvedSpacing(defaultDp = 8.dp)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        if (!node.title.isNullOrBlank() || !node.subtitle.isNullOrBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                node.title?.takeIf { it.isNotBlank() }?.let { title ->
                    Text(
                        text = title,
                        fontFamily = AmberSans,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.ink,
                    )
                }
                node.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                    Text(
                        text = subtitle,
                        fontFamily = AmberSans,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = tokens.ink3,
                    )
                }
            }
        }
        node.body?.takeIf { it.isNotBlank() }?.let { body ->
            Text(
                text = body,
                fontFamily = AmberSans,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = tokens.ink2,
            )
        }
        node.children.forEach { child ->
            IntelligentUiComponentView(child, document, state, onActionPrompt)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LayoutRowView(
    node: IntelligentUiComponentNode,
    document: IntelligentUiDocument,
    state: IntelligentUiCardState,
    onActionPrompt: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val spacing = node.style.resolvedSpacing(defaultDp = 8.dp)
    if (node.children.size <= 3) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.Top,
        ) {
            node.children.forEach { child ->
                Box(modifier = Modifier.weight(1f)) {
                    IntelligentUiComponentView(child, document, state, onActionPrompt)
                }
            }
        }
    } else {
        FlowRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            node.children.forEach { child ->
                IntelligentUiComponentView(child, document, state, onActionPrompt)
            }
        }
    }
}

@Composable
private fun LayoutColumnView(
    node: IntelligentUiComponentNode,
    document: IntelligentUiDocument,
    state: IntelligentUiCardState,
    onActionPrompt: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val spacing = node.style.resolvedSpacing(defaultDp = 8.dp)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing),
        horizontalAlignment = node.style.horizontalAlignment(),
    ) {
        node.children.forEach { child ->
            IntelligentUiComponentView(child, document, state, onActionPrompt)
        }
    }
}

@Composable
private fun LayoutGridView(
    node: IntelligentUiComponentNode,
    document: IntelligentUiDocument,
    state: IntelligentUiCardState,
    onActionPrompt: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val cols = (node.columns ?: 2).coerceIn(1, 3)
    val spacing = node.style.resolvedSpacing(defaultDp = 8.dp)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        node.title?.takeIf { it.isNotBlank() }?.let { title ->
            Text(
                text = title,
                fontFamily = AmberSans,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = LocalAmberTokens.current.ink,
            )
        }
        node.children.chunked(cols).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                rowItems.forEach { child ->
                    Box(modifier = Modifier.weight(1f)) {
                        IntelligentUiComponentView(child, document, state, onActionPrompt)
                    }
                }
                repeat(cols - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun LayoutCardView(
    node: IntelligentUiComponentNode,
    document: IntelligentUiDocument,
    state: IntelligentUiCardState,
    onActionPrompt: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    val chatTheme = LocalChatTheme.current
    val accent = chatTheme.accent.takeIf { it.isSpecified } ?: tokens.accent
    val toneColor = node.style.toneColor(tokens, accent)
    val pad = node.style.resolvedPadding(defaultDp = 12.dp)
    val spacing = node.style.resolvedSpacing(defaultDp = 8.dp)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (node.style.tone == IntelligentUiTone.NEUTRAL) tokens.surface2 else toneColor.copy(alpha = 0.08f),
        contentColor = tokens.ink,
        border = BorderStroke(
            width = 1.dp,
            color = if (node.style.tone == IntelligentUiTone.NEUTRAL) tokens.line else toneColor.copy(alpha = 0.35f),
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(pad),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            if (!node.title.isNullOrBlank() || !node.badge.isNullOrBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    node.title?.takeIf { it.isNotBlank() }?.let { title ->
                        Text(
                            text = title,
                            fontFamily = AmberSans,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = tokens.ink,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    node.badge?.takeIf { it.isNotBlank() }?.let { badge ->
                        InlineToneBadge(text = badge, color = toneColor)
                    }
                }
            }
            node.subtitle?.takeIf { it.isNotBlank() }?.let { sub ->
                Text(
                    text = sub,
                    fontFamily = AmberSans,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = tokens.ink3,
                )
            }
            node.body?.takeIf { it.isNotBlank() }?.let { body ->
                Text(
                    text = body,
                    fontFamily = AmberSans,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = tokens.ink2,
                )
            }
            node.children.forEach { child ->
                IntelligentUiComponentView(child, document, state, onActionPrompt)
            }
        }
    }
}

@Composable
private fun LayoutSplitView(
    node: IntelligentUiComponentNode,
    document: IntelligentUiDocument,
    state: IntelligentUiCardState,
    onActionPrompt: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val spacing = node.style.resolvedSpacing(defaultDp = 10.dp)
    if (node.secondaryChildren.isNotEmpty()) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                node.children.forEach { child ->
                    IntelligentUiComponentView(child, document, state, onActionPrompt)
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                node.secondaryChildren.forEach { child ->
                    IntelligentUiComponentView(child, document, state, onActionPrompt)
                }
            }
        }
    } else if (node.children.size >= 2) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.Top,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                IntelligentUiComponentView(node.children[0], document, state, onActionPrompt)
            }
            Box(modifier = Modifier.weight(1f)) {
                IntelligentUiComponentView(node.children[1], document, state, onActionPrompt)
            }
        }
    } else {
        LayoutColumnView(node, document, state, onActionPrompt, modifier)
    }
}

@Composable
private fun LayoutTabsView(
    node: IntelligentUiComponentNode,
    document: IntelligentUiDocument,
    state: IntelligentUiCardState,
    onActionPrompt: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    val chatTheme = LocalChatTheme.current
    val accent = chatTheme.accent.takeIf { it.isSpecified } ?: tokens.accent
    val tabs = node.tabs
    if (tabs.isEmpty()) {
        LayoutColumnView(node, document, state, onActionPrompt, modifier)
        return
    }
    val selectedTabId = state.activeTabByNodeId[node.id] ?: tabs.first().id
    val activeTab = tabs.firstOrNull { it.id == selectedTabId } ?: tabs.first()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .clip(RoundedCornerShape(10.dp))
                .background(tokens.surface2)
                .border(1.dp, tokens.line, RoundedCornerShape(10.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tabs.forEach { tab ->
                val active = tab.id == activeTab.id
                Box(
                    modifier = Modifier
                        .heightIn(min = 30.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (active) tokens.surface else Color.Transparent)
                        .clickable(role = Role.Tab) { state.activeTabByNodeId[node.id] = tab.id }
                        .padding(horizontal = 11.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(
                            text = tab.label,
                            fontFamily = AmberSans,
                            fontSize = 12.5.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (active) accent else tokens.ink2,
                        )
                        tab.badge?.takeIf { it.isNotBlank() }?.let { b ->
                            Text(
                                text = b,
                                fontFamily = AmberMono,
                                fontSize = 10.sp,
                                color = if (active) accent else tokens.ink3,
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            activeTab.children.forEach { child ->
                IntelligentUiComponentView(child, document, state, onActionPrompt)
            }
        }
    }
}

@Composable
private fun LayoutDisclosureView(
    node: IntelligentUiComponentNode,
    document: IntelligentUiDocument,
    state: IntelligentUiCardState,
    onActionPrompt: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    val expanded = state.expandedByNodeId[node.id] ?: node.expandedByDefault

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)),
        shape = RoundedCornerShape(12.dp),
        color = tokens.surface2,
        contentColor = tokens.ink,
        border = BorderStroke(1.dp, tokens.line),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { state.expandedByNodeId[node.id] = !expanded }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = node.title?.takeIf { it.isNotBlank() } ?: "展开详情",
                        fontFamily = AmberSans,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = tokens.ink,
                    )
                    node.subtitle?.takeIf { it.isNotBlank() }?.let { sub ->
                        Text(
                            text = sub,
                            fontFamily = AmberSans,
                            fontSize = 11.5.sp,
                            color = tokens.ink3,
                        )
                    }
                }
                Icon(
                    imageVector = Lucide.ChevronDown,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = tokens.ink3,
                )
            }
            if (expanded) {
                HorizontalDivider(thickness = 0.5.dp, color = tokens.line)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    node.body?.takeIf { it.isNotBlank() }?.let { body ->
                        Text(
                            text = body,
                            fontFamily = AmberSans,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = tokens.ink2,
                        )
                    }
                    node.children.forEach { child ->
                        IntelligentUiComponentView(child, document, state, onActionPrompt)
                    }
                }
            }
        }
    }
}

@Composable
private fun TextHeadingView(
    node: IntelligentUiComponentNode,
    isSubheading: Boolean,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    val text = node.title?.takeIf { it.isNotBlank() } ?: node.body.orEmpty()
    if (text.isBlank()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = node.style.horizontalAlignment(),
    ) {
        Text(
            text = text,
            fontFamily = AmberSans,
            fontSize = when {
                node.style.emphasis == IntelligentUiEmphasis.HERO -> 17.sp
                isSubheading -> 13.5.sp
                else -> 15.sp
            },
            lineHeight = if (isSubheading) 19.sp else 21.sp,
            fontWeight = FontWeight.SemiBold,
            color = tokens.ink,
            textAlign = node.style.textAlign(),
        )
        node.subtitle?.takeIf { it.isNotBlank() }?.let { sub ->
            Text(
                text = sub,
                fontFamily = AmberSans,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = tokens.ink3,
                textAlign = node.style.textAlign(),
            )
        }
    }
}

@Composable
private fun TextBodyView(
    node: IntelligentUiComponentNode,
    isCaption: Boolean,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    val text = node.body?.takeIf { it.isNotBlank() } ?: node.title.orEmpty()
    if (text.isBlank()) return
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        fontFamily = AmberSans,
        fontSize = if (isCaption) 11.5.sp else 13.5.sp,
        lineHeight = if (isCaption) 16.sp else 20.sp,
        fontWeight = if (node.style.emphasis == IntelligentUiEmphasis.STRONG) FontWeight.Medium else FontWeight.Normal,
        color = if (isCaption || node.style.emphasis == IntelligentUiEmphasis.SUBTLE) tokens.ink3 else tokens.ink2,
        textAlign = node.style.textAlign(),
    )
}

@Composable
private fun TextEyebrowView(
    node: IntelligentUiComponentNode,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    val text = node.title?.takeIf { it.isNotBlank() } ?: node.body.orEmpty()
    if (text.isBlank()) return
    Text(
        text = text.uppercase(),
        modifier = modifier.fillMaxWidth(),
        fontFamily = AmberMono,
        fontSize = 10.5.sp,
        letterSpacing = 0.6.sp,
        fontWeight = FontWeight.Medium,
        color = node.style.toneColor(tokens, accent),
        textAlign = node.style.textAlign(),
    )
}

@Composable
private fun TextCalloutView(
    node: IntelligentUiComponentNode,
    isQuote: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    val toneColor = node.style.toneColor(tokens, accent)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isQuote) tokens.surface2 else toneColor.copy(alpha = 0.08f))
            .border(0.5.dp, if (isQuote) tokens.line else toneColor.copy(alpha = 0.30f), RoundedCornerShape(10.dp))
            .padding(11.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .heightIn(min = 20.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(toneColor),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            node.title?.takeIf { it.isNotBlank() }?.let { title ->
                Text(
                    text = title,
                    fontFamily = AmberSans,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.ink,
                )
            }
            node.body?.takeIf { it.isNotBlank() }?.let { body ->
                Text(
                    text = body,
                    fontFamily = AmberSans,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = tokens.ink2,
                )
            }
        }
    }
}

@Composable
private fun CodeBlockNodeView(
    node: IntelligentUiComponentNode,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    val code = node.code?.takeIf { it.isNotBlank() } ?: node.body.orEmpty()
    if (code.isBlank()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(tokens.surface2)
            .border(1.dp, tokens.line, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!node.title.isNullOrBlank() || !node.language.isNullOrBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = node.title.orEmpty(),
                    fontFamily = AmberSans,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = tokens.ink3,
                )
                node.language?.takeIf { it.isNotBlank() }?.let { lang ->
                    Text(
                        text = lang,
                        fontFamily = AmberMono,
                        fontSize = 10.5.sp,
                        color = tokens.ink3,
                    )
                }
            }
        }
        Text(
            text = code,
            fontFamily = AmberMono,
            fontSize = 11.5.sp,
            lineHeight = 17.sp,
            color = tokens.ink,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        )
    }
}

@Composable
private fun ListItemsView(
    node: IntelligentUiComponentNode,
    numbered: Boolean,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    val entries = node.items.ifEmpty {
        node.body?.lines()?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        node.title?.takeIf { it.isNotBlank() }?.let { title ->
            Text(
                text = title,
                fontFamily = AmberSans,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = tokens.ink,
            )
        }
        entries.forEachIndexed { idx, item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (numbered) "${idx + 1}." else "•",
                    fontFamily = AmberMono,
                    fontSize = 12.sp,
                    lineHeight = 19.sp,
                    color = tokens.ink3,
                )
                Text(
                    text = item,
                    fontFamily = AmberSans,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = tokens.ink2,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun KeyValueSingleView(
    node: IntelligentUiComponentNode,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    val keyText = node.title?.takeIf { it.isNotBlank() } ?: node.keyValues.firstOrNull()?.key.orEmpty()
    val valText = node.valueText?.takeIf { it.isNotBlank() }
        ?: node.body?.takeIf { it.isNotBlank() }
        ?: node.keyValues.firstOrNull()?.value.orEmpty()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = keyText,
            fontFamily = AmberSans,
            fontSize = 12.5.sp,
            color = tokens.ink3,
            modifier = Modifier.weight(0.45f),
        )
        Text(
            text = listOfNotNull(valText.takeIf { it.isNotBlank() }, node.unit?.takeIf { it.isNotBlank() }).joinToString(" "),
            fontFamily = AmberMono,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            color = tokens.ink,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.55f),
        )
    }
}

@Composable
private fun DefinitionListView(
    node: IntelligentUiComponentNode,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(tokens.surface2)
            .border(1.dp, tokens.line, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        node.title?.takeIf { it.isNotBlank() }?.let { title ->
            Text(
                text = title,
                fontFamily = AmberSans,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = tokens.ink,
            )
        }
        node.keyValues.forEachIndexed { idx, kv ->
            if (idx > 0) {
                HorizontalDivider(thickness = 0.5.dp, color = tokens.line)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = kv.key,
                    fontFamily = AmberSans,
                    fontSize = 12.5.sp,
                    color = tokens.ink3,
                    modifier = Modifier.weight(0.42f),
                )
                Text(
                    text = kv.value,
                    fontFamily = AmberSans,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = tokens.ink,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(0.58f),
                )
            }
        }
    }
}

@Composable
private fun StatusPillNodeView(
    node: IntelligentUiComponentNode,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    val text = node.title?.takeIf { it.isNotBlank() } ?: node.badge ?: node.body.orEmpty()
    if (text.isBlank()) return
    val color = node.style.toneColor(tokens, accent)
    Box(modifier = modifier) {
        InlineToneBadge(text = text, color = color)
    }
}

@Composable
private fun StatusDotNodeView(
    node: IntelligentUiComponentNode,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    val color = node.style.toneColor(tokens, accent)
    val label = node.title?.takeIf { it.isNotBlank() } ?: node.body.orEmpty()
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = label,
            fontFamily = AmberSans,
            fontSize = 12.5.sp,
            color = tokens.ink2,
        )
    }
}

@Composable
private fun StatusAlertBannerView(
    node: IntelligentUiComponentNode,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    val color = node.style.toneColor(tokens, accent)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        node.title?.takeIf { it.isNotBlank() }?.let { title ->
            Text(
                text = title,
                fontFamily = AmberSans,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = color,
            )
        }
        node.body?.takeIf { it.isNotBlank() }?.let { body ->
            Text(
                text = body,
                fontFamily = AmberSans,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = tokens.ink2,
            )
        }
    }
}

@Composable
private fun StatusEmptyOrSkeletonView(
    node: IntelligentUiComponentNode,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(tokens.surface2)
            .border(1.dp, tokens.line, RoundedCornerShape(10.dp))
            .padding(14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = node.title?.takeIf { it.isNotBlank() } ?: "暂无内容",
                fontFamily = AmberSans,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = tokens.ink2,
            )
            node.body?.takeIf { it.isNotBlank() }?.let { body ->
                Text(
                    text = body,
                    fontFamily = AmberSans,
                    fontSize = 12.sp,
                    color = tokens.ink3,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun TimelineStepItemView(
    node: IntelligentUiComponentNode,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    val color = node.style.toneColor(tokens, accent)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(9.dp)
                .clip(CircleShape)
                .background(color),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = node.title.orEmpty(),
                    fontFamily = AmberSans,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.ink,
                    modifier = Modifier.weight(1f),
                )
                node.badge?.takeIf { it.isNotBlank() }?.let { b ->
                    Text(
                        text = b,
                        fontFamily = AmberMono,
                        fontSize = 10.5.sp,
                        color = tokens.ink3,
                    )
                }
            }
            node.body?.takeIf { it.isNotBlank() }?.let { body ->
                Text(
                    text = body,
                    fontFamily = AmberSans,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = tokens.ink2,
                )
            }
        }
    }
}

@Composable
private fun InputTextFieldNodeView(
    node: IntelligentUiComponentNode,
    state: IntelligentUiCardState,
    multiline: Boolean,
    numeric: Boolean,
    isDate: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    val value = state.fieldValues[node.id].orEmpty()
    val validationError = state.validationErrorFor(node)
    val borderColor = if (validationError != null) DangerToneColor else tokens.line2

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        InputFieldHeader(node)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (multiline) 72.dp else 38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tokens.surface2)
                .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                .padding(horizontal = 11.dp, vertical = 9.dp),
            contentAlignment = if (multiline) Alignment.TopStart else Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = node.placeholder?.takeIf { it.isNotBlank() }
                                ?: if (isDate) "YYYY-MM-DD" else if (numeric) "请输入数值" else "请输入…",
                            fontFamily = if (numeric || isDate) AmberMono else AmberSans,
                            fontSize = 13.sp,
                            color = tokens.ink4,
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = { state.setFieldValue(node.id, it) },
                        singleLine = !multiline,
                        textStyle = TextStyle(
                            fontFamily = if (numeric || isDate) AmberMono else AmberSans,
                            fontSize = 13.sp,
                            color = tokens.ink,
                        ),
                        cursorBrush = SolidColor(tokens.accent),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                node.unit?.takeIf { it.isNotBlank() }?.let { unit ->
                    Text(
                        text = unit,
                        fontFamily = AmberMono,
                        fontSize = 11.5.sp,
                        color = tokens.ink3,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
        if (validationError != null) {
            Text(
                text = validationError,
                fontFamily = AmberSans,
                fontSize = 11.5.sp,
                color = DangerToneColor,
            )
        }
    }
}

@Composable
private fun InputSliderNodeView(
    node: IntelligentUiComponentNode,
    state: IntelligentUiCardState,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    val min = node.min ?: 0.0
    val max = (node.max ?: 100.0).coerceAtLeast(min + 1.0)
    val currentVal = state.fieldValues[node.id]?.toDoubleOrNull()
        ?: node.numericValue
        ?: min
    val stepCount = node.step?.takeIf { it > 0.0 }?.let {
        (((max - min) / it).roundToInt() - 1).coerceIn(0, 100)
    } ?: 0

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = node.title?.takeIf { it.isNotBlank() } ?: node.id,
                fontFamily = AmberSans,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = tokens.ink,
            )
            Text(
                text = "${IntelligentUiCardState.formatNumber(currentVal)}${node.unit.orEmpty()}",
                fontFamily = AmberMono,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = accent,
            )
        }
        Slider(
            value = currentVal.toFloat().coerceIn(min.toFloat(), max.toFloat()),
            onValueChange = { next ->
                state.setNumericFieldValue(
                    fieldId = node.id,
                    value = next.toDouble(),
                    min = min,
                    max = max,
                    step = node.step,
                )
            },
            valueRange = min.toFloat()..max.toFloat(),
            steps = stepCount,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = tokens.line2,
            ),
        )
    }
}

@Composable
private fun InputStepperNodeView(
    node: IntelligentUiComponentNode,
    state: IntelligentUiCardState,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    val step = node.step?.takeIf { it > 0.0 } ?: 1.0
    val current = state.fieldValues[node.id]?.toDoubleOrNull() ?: node.numericValue ?: (node.min ?: 0.0)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(tokens.surface2)
            .border(1.dp, tokens.line, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = node.title?.takeIf { it.isNotBlank() } ?: node.id,
                fontFamily = AmberSans,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = tokens.ink,
            )
            node.subtitle?.takeIf { it.isNotBlank() }?.let { sub ->
                Text(
                    text = sub,
                    fontFamily = AmberSans,
                    fontSize = 11.5.sp,
                    color = tokens.ink3,
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StepperButton(
                symbol = "−",
                enabled = node.min == null || current - step >= node.min - 1e-9,
                onClick = { state.setNumericFieldValue(node.id, current - step, node.min, node.max, step) },
            )
            Text(
                text = "${IntelligentUiCardState.formatNumber(current)}${node.unit.orEmpty()}",
                fontFamily = AmberMono,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                modifier = Modifier.widthIn(min = 44.dp),
                textAlign = TextAlign.Center,
            )
            StepperButton(
                symbol = "+",
                enabled = node.max == null || current + step <= node.max + 1e-9,
                onClick = { state.setNumericFieldValue(node.id, current + step, node.min, node.max, step) },
            )
        }
    }
}

@Composable
private fun StepperButton(
    symbol: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tokens = LocalAmberTokens.current
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) tokens.surface else tokens.surface2)
            .border(1.dp, if (enabled) tokens.line2 else tokens.line, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            fontFamily = AmberMono,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) tokens.ink else tokens.ink4,
        )
    }
}

@Composable
private fun InputRatingNodeView(
    node: IntelligentUiComponentNode,
    state: IntelligentUiCardState,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    val maxStars = (node.max?.roundToInt() ?: 5).coerceIn(3, 10)
    val current = (state.fieldValues[node.id]?.toDoubleOrNull() ?: node.numericValue ?: 4.0).roundToInt()

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = node.title?.takeIf { it.isNotBlank() } ?: "评分",
            fontFamily = AmberSans,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = tokens.ink,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (star in 1..maxStars) {
                val active = star <= current
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (active) accent.copy(alpha = 0.14f) else tokens.surface2)
                        .border(1.dp, if (active) accent else tokens.line, RoundedCornerShape(6.dp))
                        .clickable(role = Role.Button) {
                            state.setNumericFieldValue(node.id, star.toDouble(), 1.0, maxStars.toDouble(), 1.0)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "★",
                        fontSize = 13.sp,
                        color = if (active) accent else tokens.ink4,
                    )
                }
            }
        }
    }
}

@Composable
private fun InputToggleCheckboxView(
    node: IntelligentUiComponentNode,
    state: IntelligentUiCardState,
    useSwitch: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    val checked = state.fieldValues[node.id] == "true"
    val validationError = state.validationErrorFor(node)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(tokens.surface2)
                .border(1.dp, if (validationError != null) DangerToneColor else tokens.line, RoundedCornerShape(10.dp))
                .clickable(role = if (useSwitch) Role.Switch else Role.Checkbox) {
                    state.setFieldValue(node.id, (!checked).toString())
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = node.title?.takeIf { it.isNotBlank() } ?: node.body ?: node.id,
                        fontFamily = AmberSans,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = tokens.ink,
                    )
                    if (node.required) {
                        Text(
                            text = "*",
                            fontFamily = AmberMono,
                            fontSize = 12.sp,
                            color = DangerToneColor,
                        )
                    }
                }
                node.subtitle?.takeIf { it.isNotBlank() }?.let { sub ->
                    Text(
                        text = sub,
                        fontFamily = AmberSans,
                        fontSize = 11.5.sp,
                        color = tokens.ink3,
                    )
                }
            }
            if (useSwitch) {
                Switch(
                    checked = checked,
                    onCheckedChange = { state.setFieldValue(node.id, it.toString()) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = accent,
                    ),
                )
            } else {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { state.setFieldValue(node.id, it.toString()) },
                    colors = CheckboxDefaults.colors(checkedColor = accent),
                )
            }
        }
        if (validationError != null) {
            Text(
                text = validationError,
                fontFamily = AmberSans,
                fontSize = 11.5.sp,
                color = DangerToneColor,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InputSingleChoiceView(
    node: IntelligentUiComponentNode,
    state: IntelligentUiCardState,
    segmented: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    val selected = state.fieldValues[node.id].orEmpty()
    val validationError = state.validationErrorFor(node)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        InputFieldHeader(node)
        if (segmented) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                node.options.forEach { opt ->
                    val isSelected = selected == opt.value || selected == opt.id
                    ChoiceChipPill(
                        label = opt.label,
                        selected = isSelected,
                        accent = accent,
                        onClick = { state.setFieldValue(node.id, opt.value) },
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                node.options.forEach { opt ->
                    val isSelected = selected == opt.value || selected == opt.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) accent.copy(alpha = 0.10f) else tokens.surface2)
                            .border(1.dp, if (isSelected) accent else tokens.line, RoundedCornerShape(10.dp))
                            .clickable(role = Role.RadioButton) { state.setFieldValue(node.id, opt.value) }
                            .padding(horizontal = 11.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, if (isSelected) accent else tokens.ink3, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(accent),
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = opt.label,
                                fontFamily = AmberSans,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = tokens.ink,
                            )
                            opt.description?.takeIf { it.isNotBlank() }?.let { desc ->
                                Text(
                                    text = desc,
                                    fontFamily = AmberSans,
                                    fontSize = 11.5.sp,
                                    color = tokens.ink3,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (validationError != null) {
            Text(
                text = validationError,
                fontFamily = AmberSans,
                fontSize = 11.5.sp,
                color = DangerToneColor,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InputMultiChoiceView(
    node: IntelligentUiComponentNode,
    state: IntelligentUiCardState,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val selectedSet = state.multiFieldValues[node.id].orEmpty()
    val validationError = state.validationErrorFor(node)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        InputFieldHeader(node)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            node.options.forEach { opt ->
                val isSelected = selectedSet.contains(opt.value) || selectedSet.contains(opt.id)
                ChoiceChipPill(
                    label = opt.label,
                    selected = isSelected,
                    accent = accent,
                    onClick = { state.toggleMultiOption(node.id, opt.value) },
                )
            }
        }
        if (validationError != null) {
            Text(
                text = validationError,
                fontFamily = AmberSans,
                fontSize = 11.5.sp,
                color = DangerToneColor,
            )
        }
    }
}

@Composable
private fun ChoiceChipPill(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val tokens = LocalAmberTokens.current
    Box(
        modifier = Modifier
            .heightIn(min = 32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) accent.copy(alpha = 0.14f) else tokens.surface2)
            .border(1.dp, if (selected) accent else tokens.line, RoundedCornerShape(8.dp))
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontFamily = AmberSans,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) accent else tokens.ink2,
        )
    }
}

@Composable
private fun InputFieldHeader(node: IntelligentUiComponentNode) {
    val tokens = LocalAmberTokens.current
    val title = node.title?.takeIf { it.isNotBlank() } ?: return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            fontFamily = AmberSans,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            color = tokens.ink,
        )
        if (node.required) {
            Text(
                text = "*",
                fontFamily = AmberMono,
                fontSize = 12.sp,
                color = DangerToneColor,
            )
        }
    }
}

@Composable
private fun DataMetricNodeView(
    node: IntelligentUiComponentNode,
    document: IntelligentUiDocument,
    state: IntelligentUiCardState,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    val boundNum = state.boundNumericValue(
        document = document,
        valueFieldId = node.valueFieldId,
        fallback = node.numericValue,
        scale = node.scale,
        offset = node.offset,
    )
    val displayValue = if (boundNum != null) {
        IntelligentUiCardState.formatNumber(boundNum)
    } else {
        node.valueText?.takeIf { it.isNotBlank() } ?: node.body ?: "—"
    }
    val toneColor = node.style.toneColor(tokens, accent)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = tokens.surface2,
        contentColor = tokens.ink,
        border = BorderStroke(1.dp, tokens.line),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            node.title?.takeIf { it.isNotBlank() }?.let { title ->
                Text(
                    text = title,
                    fontFamily = AmberSans,
                    fontSize = 12.sp,
                    color = tokens.ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = displayValue,
                    fontFamily = AmberMono,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (node.style.tone == IntelligentUiTone.NEUTRAL) tokens.ink else toneColor,
                )
                node.unit?.takeIf { it.isNotBlank() }?.let { u ->
                    Text(
                        text = u,
                        fontFamily = AmberMono,
                        fontSize = 11.5.sp,
                        color = tokens.ink3,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
            }
            val meta = node.delta ?: node.subtitle ?: node.badge
            meta?.takeIf { it.isNotBlank() }?.let { delta ->
                Text(
                    text = delta,
                    fontFamily = AmberMono,
                    fontSize = 11.sp,
                    color = toneColor,
                )
            }
        }
    }
}

@Composable
private fun DataMetricGridView(
    node: IntelligentUiComponentNode,
    document: IntelligentUiDocument,
    state: IntelligentUiCardState,
    onActionPrompt: ((String) -> Unit)?,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    if (node.children.isNotEmpty()) {
        LayoutGridView(node, document, state, onActionPrompt, modifier)
        return
    }
    val cols = (node.columns ?: 2).coerceIn(1, 3)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        node.dataPoints.chunked(cols).forEach { rowPoints ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowPoints.forEach { pt ->
                    val synthetic = IntelligentUiComponentNode(
                        id = "${node.id}_${pt.label}",
                        kind = IntelligentUiComponentKind.METRIC,
                        title = pt.label,
                        numericValue = pt.value,
                        unit = pt.unit,
                        valueFieldId = pt.valueFieldId,
                        scale = pt.scale,
                        offset = pt.offset,
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        DataMetricNodeView(synthetic, document, state, accent)
                    }
                }
                repeat(cols - rowPoints.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DataProgressNodeView(
    node: IntelligentUiComponentNode,
    document: IntelligentUiDocument,
    state: IntelligentUiCardState,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    val maxVal = (node.max ?: 100.0).coerceAtLeast(1.0)
    val currentVal = state.boundNumericValue(
        document = document,
        valueFieldId = node.valueFieldId,
        fallback = node.numericValue ?: 0.0,
        scale = node.scale,
        offset = node.offset,
    ) ?: 0.0
    val fraction = (currentVal / maxVal).toFloat().coerceIn(0f, 1f)
    val toneColor = node.style.toneColor(tokens, accent)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = node.title?.takeIf { it.isNotBlank() } ?: "进度",
                fontFamily = AmberSans,
                fontSize = 12.5.sp,
                color = tokens.ink2,
            )
            Text(
                text = "${IntelligentUiCardState.formatNumber(currentVal)}${node.unit ?: "%"}",
                fontFamily = AmberMono,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = toneColor,
            )
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = toneColor,
            trackColor = tokens.line2,
        )
    }
}

@Composable
private fun DataStepProgressNodeView(
    node: IntelligentUiComponentNode,
    document: IntelligentUiDocument,
    state: IntelligentUiCardState,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    val totalSteps = max(node.items.size, node.max?.roundToInt() ?: 4).coerceIn(2, 8)
    val activeStep = (state.boundNumericValue(
        document = document,
        valueFieldId = node.valueFieldId,
        fallback = node.numericValue ?: 1.0,
        scale = node.scale,
        offset = node.offset,
    )?.roundToInt() ?: 1).coerceIn(0, totalSteps)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        node.title?.takeIf { it.isNotBlank() }?.let { title ->
            Text(
                text = title,
                fontFamily = AmberSans,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = tokens.ink,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            for (stepIdx in 1..totalSteps) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (stepIdx <= activeStep) accent else tokens.line2),
                )
            }
        }
    }
}

@Composable
private fun DataComparisonRowView(
    node: IntelligentUiComponentNode,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    val left = node.leftLabel ?: node.keyValues.getOrNull(0)?.value.orEmpty()
    val right = node.rightLabel ?: node.keyValues.getOrNull(1)?.value.orEmpty()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(tokens.surface2)
            .border(1.dp, tokens.line, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        node.title?.takeIf { it.isNotBlank() }?.let { title ->
            Text(
                text = title,
                fontFamily = AmberSans,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = tokens.ink3,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = left,
                fontFamily = AmberSans,
                fontSize = 12.5.sp,
                color = tokens.ink,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "vs",
                fontFamily = AmberMono,
                fontSize = 11.sp,
                color = tokens.ink3,
            )
            Text(
                text = right,
                fontFamily = AmberSans,
                fontSize = 12.5.sp,
                color = tokens.ink,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DataProsConsView(
    node: IntelligentUiComponentNode,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(PositiveToneColor.copy(alpha = 0.08f))
                .border(1.dp, PositiveToneColor.copy(alpha = 0.30f), RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "优势 Pros",
                fontFamily = AmberMono,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = PositiveToneColor,
            )
            node.pros.forEach { pro ->
                Text(
                    text = "+ $pro",
                    fontFamily = AmberSans,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = tokens.ink,
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(WarningToneColor.copy(alpha = 0.08f))
                .border(1.dp, WarningToneColor.copy(alpha = 0.30f), RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "局限 Cons",
                fontFamily = AmberMono,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = WarningToneColor,
            )
            node.cons.forEach { con ->
                Text(
                    text = "− $con",
                    fontFamily = AmberSans,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = tokens.ink,
                )
            }
        }
    }
}

@Composable
private fun DataTableView(
    node: IntelligentUiComponentNode,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    val headers = node.headers
    val rows = node.rows
    if (headers.isEmpty() && rows.isEmpty()) return
    val colCount = max(headers.size, rows.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(tokens.surface2)
            .border(1.dp, tokens.line, RoundedCornerShape(10.dp)),
    ) {
        if (headers.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tokens.raised)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (i in 0 until colCount) {
                    Text(
                        text = headers.getOrNull(i).orEmpty(),
                        fontFamily = AmberSans,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.ink,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        rows.forEachIndexed { rIdx, row ->
            if (rIdx > 0 || headers.isNotEmpty()) {
                HorizontalDivider(thickness = 0.5.dp, color = tokens.line)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (i in 0 until colCount) {
                    Text(
                        text = row.getOrNull(i).orEmpty(),
                        fontFamily = AmberSans,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = tokens.ink2,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DataRankingListView(
    node: IntelligentUiComponentNode,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        node.title?.takeIf { it.isNotBlank() }?.let { title ->
            Text(
                text = title,
                fontFamily = AmberSans,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = tokens.ink,
            )
        }
        if (node.dataPoints.isNotEmpty()) {
            node.dataPoints.forEachIndexed { idx, pt ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .background(tokens.surface2)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "#${idx + 1}",
                        fontFamily = AmberMono,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (idx == 0) accent else tokens.ink3,
                    )
                    Text(
                        text = pt.label,
                        fontFamily = AmberSans,
                        fontSize = 12.5.sp,
                        color = tokens.ink,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${IntelligentUiCardState.formatNumber(pt.value)}${pt.unit.orEmpty()}",
                        fontFamily = AmberMono,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = tokens.ink2,
                    )
                }
            }
        } else {
            ListItemsView(node, numbered = true)
        }
    }
}

@Composable
private fun DataBarChartView(
    node: IntelligentUiComponentNode,
    document: IntelligentUiDocument,
    state: IntelligentUiCardState,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    val resolvedPoints = node.dataPoints.map { pt ->
        val v = state.boundNumericValue(
            document = document,
            valueFieldId = pt.valueFieldId ?: node.valueFieldId,
            fallback = pt.value,
            scale = pt.scale,
            offset = pt.offset,
        ) ?: pt.value
        pt.copy(value = v)
    }
    if (resolvedPoints.isEmpty()) return
    val maxVal = (resolvedPoints.maxOfOrNull { it.value } ?: 1.0).coerceAtLeast(1.0)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tokens.surface2)
            .border(1.dp, tokens.line, RoundedCornerShape(12.dp))
            .padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        node.title?.takeIf { it.isNotBlank() }?.let { title ->
            Text(
                text = title,
                fontFamily = AmberSans,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = tokens.ink,
            )
        }
        resolvedPoints.forEach { pt ->
            val ratio = (pt.value / maxVal).toFloat().coerceIn(0.04f, 1f)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = pt.label,
                        fontFamily = AmberSans,
                        fontSize = 12.sp,
                        color = tokens.ink2,
                    )
                    Text(
                        text = "${IntelligentUiCardState.formatNumber(pt.value)}${pt.unit ?: node.unit.orEmpty()}",
                        fontFamily = AmberMono,
                        fontSize = 11.5.sp,
                        color = tokens.ink,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(tokens.line),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(ratio)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(999.dp))
                            .background(accent),
                    )
                }
            }
        }
    }
}

@Composable
private fun DataLineChartView(
    node: IntelligentUiComponentNode,
    document: IntelligentUiDocument,
    state: IntelligentUiCardState,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    val resolvedPoints = node.dataPoints.map { pt ->
        val v = state.boundNumericValue(
            document = document,
            valueFieldId = pt.valueFieldId ?: node.valueFieldId,
            fallback = pt.value,
            scale = pt.scale,
            offset = pt.offset,
        ) ?: pt.value
        pt.copy(value = v)
    }
    if (resolvedPoints.isEmpty()) return
    val minVal = resolvedPoints.minOf { it.value }
    val maxVal = resolvedPoints.maxOf { it.value }
    val span = (maxVal - minVal).takeIf { it > 1e-6 } ?: 1.0

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tokens.surface2)
            .border(1.dp, tokens.line, RoundedCornerShape(12.dp))
            .padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        node.title?.takeIf { it.isNotBlank() }?.let { title ->
            Text(
                text = title,
                fontFamily = AmberSans,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = tokens.ink,
            )
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp),
        ) {
            val count = resolvedPoints.size
            val path = Path()
            resolvedPoints.forEachIndexed { idx, pt ->
                val x = if (count == 1) size.width / 2f else (idx.toFloat() / (count - 1)) * size.width
                val normY = ((pt.value - minVal) / span).toFloat().coerceIn(0f, 1f)
                val y = size.height - normY * (size.height - 12.dp.toPx()) - 6.dp.toPx()
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                drawCircle(color = accent, radius = 3.dp.toPx(), center = Offset(x, y))
            }
            if (count > 1) {
                drawPath(path = path, color = accent, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            resolvedPoints.take(6).forEach { pt ->
                Text(
                    text = pt.label,
                    fontFamily = AmberMono,
                    fontSize = 10.sp,
                    color = tokens.ink3,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun MediaIconLabelView(
    node: IntelligentUiComponentNode,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = node.icon?.take(2)?.ifBlank { "✦" } ?: "✦",
                fontFamily = AmberMono,
                fontSize = 11.sp,
                color = accent,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = node.title?.takeIf { it.isNotBlank() } ?: node.body.orEmpty(),
                fontFamily = AmberSans,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = tokens.ink,
            )
            node.subtitle?.takeIf { it.isNotBlank() }?.let { sub ->
                Text(
                    text = sub,
                    fontFamily = AmberSans,
                    fontSize = 11.5.sp,
                    color = tokens.ink3,
                )
            }
        }
    }
}

@Composable
private fun MediaAvatarItemView(
    node: IntelligentUiComponentNode,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    val initial = (node.title?.trim()?.firstOrNull()?.uppercaseChar() ?: 'A').toString()
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initial,
                fontFamily = AmberMono,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = accent,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = node.title.orEmpty(),
                fontFamily = AmberSans,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = tokens.ink,
            )
            (node.subtitle ?: node.body)?.takeIf { it.isNotBlank() }?.let { sub ->
                Text(
                    text = sub,
                    fontFamily = AmberSans,
                    fontSize = 12.sp,
                    color = tokens.ink3,
                )
            }
        }
        node.badge?.takeIf { it.isNotBlank() }?.let { b ->
            InlineToneBadge(text = b, color = accent)
        }
    }
}

@Composable
private fun MediaImageCardView(
    node: IntelligentUiComponentNode,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tokens.surface2)
            .border(1.dp, tokens.line, RoundedCornerShape(12.dp))
            .padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = node.title?.takeIf { it.isNotBlank() } ?: "图像预览",
            fontFamily = AmberSans,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = tokens.ink,
        )
        (node.body ?: node.subtitle ?: node.imageUrl)?.takeIf { it.isNotBlank() }?.let { caption ->
            Text(
                text = caption,
                fontFamily = AmberSans,
                fontSize = 12.sp,
                color = tokens.ink3,
            )
        }
    }
}

@Composable
private fun MediaGalleryStripView(
    node: IntelligentUiComponentNode,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    val items = node.items.ifEmpty { node.options.map { it.label } }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { label ->
            Box(
                modifier = Modifier
                    .widthIn(min = 100.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tokens.surface2)
                    .border(1.dp, tokens.line, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    text = label,
                    fontFamily = AmberSans,
                    fontSize = 12.sp,
                    color = tokens.ink2,
                )
            }
        }
    }
}

@Composable
private fun MediaLinkOrFileCardView(
    node: IntelligentUiComponentNode,
    isFile: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    val uriHandler = LocalUriHandler.current
    val url = node.url?.takeIf { it.isNotBlank() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(tokens.surface2)
            .border(1.dp, tokens.line, RoundedCornerShape(10.dp))
            .then(
                if (url != null) {
                    Modifier.clickable(role = Role.Button) {
                        runCatching { uriHandler.openUri(url) }
                    }
                } else {
                    Modifier
                },
            )
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = if (isFile) Lucide.FileText else Lucide.ExternalLink,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = accent,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = node.title?.takeIf { it.isNotBlank() } ?: url ?: "外部资源",
                fontFamily = AmberSans,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = tokens.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            (node.subtitle ?: node.body ?: url)?.takeIf { it.isNotBlank() }?.let { sub ->
                Text(
                    text = sub,
                    fontFamily = AmberMono,
                    fontSize = 11.sp,
                    color = tokens.ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MediaMapPinView(
    node: IntelligentUiComponentNode,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(tokens.surface2)
            .border(1.dp, tokens.line, RoundedCornerShape(10.dp))
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(
            imageVector = Lucide.MapPin,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = accent,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = node.title?.takeIf { it.isNotBlank() } ?: "位置标记",
                fontFamily = AmberSans,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = tokens.ink,
            )
            (node.subtitle ?: node.body)?.takeIf { it.isNotBlank() }?.let { addr ->
                Text(
                    text = addr,
                    fontFamily = AmberSans,
                    fontSize = 12.sp,
                    color = tokens.ink3,
                )
            }
        }
    }
}

@Composable
private fun SemanticOptionCardView(
    node: IntelligentUiComponentNode,
    state: IntelligentUiCardState,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    val selected = state.selectedDecisionOptionId == node.id

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.RadioButton) {
                state.selectedDecisionOptionId = node.id
                state.submittedSummary = null
            },
        shape = RoundedCornerShape(12.dp),
        color = if (selected) accent.copy(alpha = 0.10f) else tokens.surface2,
        contentColor = tokens.ink,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) accent else tokens.line),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = node.title?.takeIf { it.isNotBlank() } ?: node.id,
                    fontFamily = AmberSans,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.ink,
                    modifier = Modifier.weight(1f),
                )
                val badge = node.badge ?: if (node.recommended) "推荐" else null
                badge?.let { b ->
                    InlineToneBadge(text = b, color = accent)
                }
            }
            (node.body ?: node.subtitle)?.takeIf { it.isNotBlank() }?.let { desc ->
                Text(
                    text = desc,
                    fontFamily = AmberSans,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = tokens.ink2,
                )
            }
            if (node.pros.isNotEmpty() || node.cons.isNotEmpty()) {
                DataProsConsView(node)
            }
        }
    }
}

@Composable
private fun SemanticRecommendationHeroView(
    node: IntelligentUiComponentNode,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.10f))
            .border(1.dp, accent.copy(alpha = 0.40f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = node.badge?.takeIf { it.isNotBlank() } ?: "推荐结论",
                fontFamily = AmberMono,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = accent,
            )
        }
        node.title?.takeIf { it.isNotBlank() }?.let { title ->
            Text(
                text = title,
                fontFamily = AmberSans,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = tokens.ink,
            )
        }
        node.body?.takeIf { it.isNotBlank() }?.let { body ->
            Text(
                text = body,
                fontFamily = AmberSans,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = tokens.ink2,
            )
        }
    }
}

@Composable
private fun SemanticConstraintOrRiskView(
    node: IntelligentUiComponentNode,
    isRisk: Boolean,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    val color = if (isRisk) DangerToneColor else WarningToneColor
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.30f), RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        InlineToneBadge(text = node.badge ?: if (isRisk) "风险" else "约束", color = color)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            node.title?.takeIf { it.isNotBlank() }?.let { title ->
                Text(
                    text = title,
                    fontFamily = AmberSans,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.ink,
                )
            }
            node.body?.takeIf { it.isNotBlank() }?.let { body ->
                Text(
                    text = body,
                    fontFamily = AmberSans,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = tokens.ink2,
                )
            }
        }
    }
}

@Composable
private fun ActionButtonNodeView(
    node: IntelligentUiComponentNode,
    document: IntelligentUiDocument,
    state: IntelligentUiCardState,
    onActionPrompt: ((String) -> Unit)?,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    val label = node.actionLabel?.takeIf { it.isNotBlank() }
        ?: node.title?.takeIf { it.isNotBlank() }
        ?: "执行操作"
    val customPrompt = node.actionPrompt?.takeIf { it.isNotBlank() }
    val enabled = onActionPrompt != null && (customPrompt != null || state.validationErrors(document).isEmpty())

    Box(
        modifier = modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (enabled) accent.copy(alpha = 0.14f) else tokens.surface2)
            .border(1.dp, if (enabled) accent.copy(alpha = 0.50f) else tokens.line, RoundedCornerShape(9.dp))
            .clickable(enabled = enabled, role = Role.Button) {
                val prompt = customPrompt ?: state.buildSubmissionPrompt(document).also {
                    state.submittedSummary = it
                }
                onActionPrompt?.invoke(prompt)
            }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontFamily = AmberSans,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) accent else tokens.ink4,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionBarNodeView(
    node: IntelligentUiComponentNode,
    document: IntelligentUiDocument,
    state: IntelligentUiCardState,
    onActionPrompt: ((String) -> Unit)?,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    if (node.children.isNotEmpty()) {
        FlowRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            node.children.forEach { child ->
                IntelligentUiComponentView(child, document, state, onActionPrompt)
            }
        }
    } else if (node.options.isNotEmpty()) {
        FlowRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            node.options.forEach { opt ->
                val synthetic = IntelligentUiComponentNode(
                    id = opt.id,
                    kind = IntelligentUiComponentKind.ACTION_BUTTON,
                    title = opt.label,
                    actionInstruction = opt.description ?: opt.label,
                )
                ActionButtonNodeView(synthetic, document, state, onActionPrompt, accent)
            }
        }
    } else if (!node.title.isNullOrBlank() || !node.actionInstruction.isNullOrBlank()) {
        ActionButtonNodeView(node, document, state, onActionPrompt, accent, modifier)
    }
}

@Composable
private fun SummaryOrCtaCardView(
    node: IntelligentUiComponentNode,
    document: IntelligentUiDocument,
    state: IntelligentUiCardState,
    onActionPrompt: ((String) -> Unit)?,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    LayoutCardView(node, document, state, onActionPrompt, modifier)
}

@Composable
internal fun InlineToneBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            fontFamily = AmberMono,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            color = color,
        )
    }
}

private fun IntelligentUiStyle.toneColor(tokens: AmberTokens, accent: Color): Color = when (tone) {
    IntelligentUiTone.ACCENT -> accent
    IntelligentUiTone.POSITIVE -> PositiveToneColor
    IntelligentUiTone.WARNING -> WarningToneColor
    IntelligentUiTone.DANGER -> DangerToneColor
    IntelligentUiTone.INFO -> InfoToneColor
    IntelligentUiTone.NEUTRAL -> tokens.ink2
}

private fun IntelligentUiStyle.resolvedSpacing(defaultDp: Dp): Dp {
    val sp = spacingDp
    return when {
        sp != null -> sp.coerceIn(2, 24).dp
        density == IntelligentUiDensity.COMPACT -> 6.dp
        density == IntelligentUiDensity.SPACIOUS -> 12.dp
        else -> defaultDp
    }
}

private fun IntelligentUiStyle.resolvedPadding(defaultDp: Dp): Dp {
    val pd = paddingDp
    return when {
        pd != null -> pd.coerceIn(4, 24).dp
        density == IntelligentUiDensity.COMPACT -> 9.dp
        density == IntelligentUiDensity.SPACIOUS -> 15.dp
        else -> defaultDp
    }
}

private fun IntelligentUiStyle.horizontalAlignment(): Alignment.Horizontal = when (alignment) {
    IntelligentUiAlignment.CENTER -> Alignment.CenterHorizontally
    IntelligentUiAlignment.END -> Alignment.End
    IntelligentUiAlignment.START -> Alignment.Start
}

private fun IntelligentUiStyle.textAlign(): TextAlign = when (alignment) {
    IntelligentUiAlignment.CENTER -> TextAlign.Center
    IntelligentUiAlignment.END -> TextAlign.End
    IntelligentUiAlignment.START -> TextAlign.Start
}
