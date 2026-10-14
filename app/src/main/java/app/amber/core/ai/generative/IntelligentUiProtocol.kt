package app.amber.core.ai.generative

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Top-level task archetypes for Amber's declarative `task-ui` protocol.
 */
enum class TaskUiKind(val wireName: String, val displayLabel: String) {
    DECISION("decision", "方案决策"),
    FORM("form", "信息收集"),
    EXPLORE("explore", "交互探索"),
    STUDY("study", "互动学习");

    companion object {
        fun fromRaw(raw: String?): TaskUiKind? {
            val normalized = raw?.trim()?.lowercase()?.replace('-', '_')?.replace(' ', '_') ?: return null
            return when (normalized) {
                "decision", "decide", "compare", "comparison", "choice", "options" -> DECISION
                "form", "input", "survey", "questionnaire", "collect" -> FORM
                "explore", "explorer", "browse", "inspect", "dashboard", "parameters", "simulator" -> EXPLORE
                "study", "learn", "quiz", "flashcard", "flashcards", "review", "practice", "exam" -> STUDY
                else -> null
            }
        }
    }
}

/**
 * Category grouping for the 80 native `task-ui` components.
 */
enum class IntelligentUiCategory {
    LAYOUT,
    TEXT,
    STATUS,
    INPUT,
    DATA,
    MEDIA,
    TASK_SEMANTIC,
    ACTION_CONTAINER,
}

/**
 * Complete 80-component catalog aligned across iOS, HarmonyOS, and Android:
 * - Layout (10)
 * - Text (12)
 * - Status (10)
 * - Input (14)
 * - Data (10)
 * - Media (8)
 * - Task Semantic (8)
 * - Action & Container (8)
 */
enum class IntelligentUiComponentKind(
    val canonicalName: String,
    val category: IntelligentUiCategory,
    val aliases: Set<String> = emptySet(),
) {
    // 1. Layout (10)
    SECTION("section", IntelligentUiCategory.LAYOUT, setOf("group", "block", "panel")),
    ROW("row", IntelligentUiCategory.LAYOUT, setOf("hstack", "horizontal")),
    COLUMN("column", IntelligentUiCategory.LAYOUT, setOf("vstack", "vertical", "stack")),
    GRID("grid", IntelligentUiCategory.LAYOUT, setOf("bento", "columns")),
    CARD("card", IntelligentUiCategory.LAYOUT, setOf("box", "surface", "container")),
    SPLIT("split", IntelligentUiCategory.LAYOUT, setOf("two_column", "twocolumn", "split_view")),
    TABS("tabs", IntelligentUiCategory.LAYOUT, setOf("tab_view", "tabview", "tab_group")),
    ACCORDION("accordion", IntelligentUiCategory.LAYOUT, setOf("collapse_group", "faq")),
    DISCLOSURE("disclosure", IntelligentUiCategory.LAYOUT, setOf("collapsible", "details", "expandable")),
    DIVIDER("divider", IntelligentUiCategory.LAYOUT, setOf("separator", "hr", "line")),

    // 2. Text (12)
    HEADING("heading", IntelligentUiCategory.TEXT, setOf("title", "h1", "h2", "header")),
    SUBHEADING("subheading", IntelligentUiCategory.TEXT, setOf("subtitle", "h3", "h4")),
    TEXT("text", IntelligentUiCategory.TEXT, setOf("paragraph", "body", "p", "prose", "markdown")),
    CAPTION("caption", IntelligentUiCategory.TEXT, setOf("small", "helper_text", "subtext")),
    EYEBROW("eyebrow", IntelligentUiCategory.TEXT, setOf("kicker", "overline", "section_label")),
    CALLOUT("callout", IntelligentUiCategory.TEXT, setOf("tip", "notice", "aside")),
    QUOTE("quote", IntelligentUiCategory.TEXT, setOf("blockquote", "citation")),
    CODE_BLOCK("codeBlock", IntelligentUiCategory.TEXT, setOf("code_block", "codeblock", "code", "pre")),
    BULLET_LIST("bulletList", IntelligentUiCategory.TEXT, setOf("bullet_list", "bulletlist", "ul", "unordered_list", "list")),
    NUMBERED_LIST("numberedList", IntelligentUiCategory.TEXT, setOf("numbered_list", "numberedlist", "ol", "ordered_list")),
    KEY_VALUE("keyValue", IntelligentUiCategory.TEXT, setOf("key_value", "keyvalue", "kv", "property_row")),
    DEFINITION_LIST("definitionList", IntelligentUiCategory.TEXT, setOf("definition_list", "definitionlist", "dl", "kv_list", "specs")),

    // 3. Status (10)
    BADGE("badge", IntelligentUiCategory.STATUS, setOf("status_badge")),
    TAG("tag", IntelligentUiCategory.STATUS, setOf("label_tag")),
    PILL("pill", IntelligentUiCategory.STATUS, setOf("chip_pill", "capsule")),
    STATUS_DOT("statusDot", IntelligentUiCategory.STATUS, setOf("status_dot", "statusdot", "indicator")),
    ALERT("alert", IntelligentUiCategory.STATUS, setOf("warning_box", "error_box", "info_box")),
    BANNER("banner", IntelligentUiCategory.STATUS, setOf("top_banner", "notice_banner")),
    EMPTY_STATE("emptyState", IntelligentUiCategory.STATUS, setOf("empty_state", "emptystate", "placeholder")),
    SKELETON_NOTE("skeletonNote", IntelligentUiCategory.STATUS, setOf("skeleton_note", "skeletonnote", "loading_note")),
    TIMELINE_ITEM("timelineItem", IntelligentUiCategory.STATUS, setOf("timeline_item", "timelineitem", "timeline")),
    STEP_ITEM("stepItem", IntelligentUiCategory.STATUS, setOf("step_item", "stepitem", "step")),

    // 4. Input (14)
    TEXT_FIELD("textField", IntelligentUiCategory.INPUT, setOf("text_field", "textfield", "text_input", "input")),
    TEXT_AREA("textArea", IntelligentUiCategory.INPUT, setOf("text_area", "textarea", "multiline_input", "long_text")),
    NUMBER_FIELD("numberField", IntelligentUiCategory.INPUT, setOf("number_field", "numberfield", "number_input", "number", "numeric_field")),
    SLIDER("slider", IntelligentUiCategory.INPUT, setOf("range", "range_slider")),
    STEPPER("stepper", IntelligentUiCategory.INPUT, setOf("counter", "number_stepper")),
    TOGGLE("toggle", IntelligentUiCategory.INPUT, setOf("switch", "boolean")),
    CHECKBOX("checkbox", IntelligentUiCategory.INPUT, setOf("check")),
    RADIO_GROUP("radioGroup", IntelligentUiCategory.INPUT, setOf("radio_group", "radiogroup", "radio", "single_choice")),
    SEGMENTED_CONTROL("segmentedControl", IntelligentUiCategory.INPUT, setOf("segmented_control", "segmentedcontrol", "segmented")),
    SELECT("select", IntelligentUiCategory.INPUT, setOf("dropdown", "picker", "single_select")),
    MULTI_SELECT("multiSelect", IntelligentUiCategory.INPUT, setOf("multi_select", "multiselect", "checkbox_group")),
    CHIP_GROUP("chipGroup", IntelligentUiCategory.INPUT, setOf("chip_group", "chipgroup", "chips", "tag_select")),
    DATE_FIELD("dateField", IntelligentUiCategory.INPUT, setOf("date_field", "datefield", "date", "date_picker")),
    RATING("rating", IntelligentUiCategory.INPUT, setOf("stars", "star_rating", "score_input")),

    // 5. Data (10)
    METRIC("metric", IntelligentUiCategory.DATA, setOf("stat", "kpi", "stat_card")),
    METRIC_GRID("metricGrid", IntelligentUiCategory.DATA, setOf("metric_grid", "metricgrid", "stats_grid", "kpi_grid")),
    PROGRESS("progress", IntelligentUiCategory.DATA, setOf("progress_bar", "progressbar", "meter")),
    STEP_PROGRESS("stepProgress", IntelligentUiCategory.DATA, setOf("step_progress", "stepprogress", "stage_progress")),
    COMPARISON_ROW("comparisonRow", IntelligentUiCategory.DATA, setOf("comparison_row", "comparisonrow", "compare_row")),
    PROS_CONS("prosCons", IntelligentUiCategory.DATA, setOf("pros_cons", "proscons", "tradeoffs")),
    TABLE("table", IntelligentUiCategory.DATA, setOf("data_table", "datatable")),
    RANKING_LIST("rankingList", IntelligentUiCategory.DATA, setOf("ranking_list", "rankinglist", "leaderboard", "rank_list")),
    BAR_CHART("barChart", IntelligentUiCategory.DATA, setOf("bar_chart", "barchart", "column_chart")),
    LINE_CHART("lineChart", IntelligentUiCategory.DATA, setOf("line_chart", "linechart", "trend_chart", "sparkline")),

    // 6. Media (8)
    ICON_LABEL("iconLabel", IntelligentUiCategory.MEDIA, setOf("icon_label", "iconlabel", "icon_row")),
    AVATAR_ITEM("avatarItem", IntelligentUiCategory.MEDIA, setOf("avatar_item", "avataritem", "avatar", "profile_row")),
    IMAGE_CARD("imageCard", IntelligentUiCategory.MEDIA, setOf("image_card", "imagecard", "image", "photo_card")),
    GALLERY_STRIP("galleryStrip", IntelligentUiCategory.MEDIA, setOf("gallery_strip", "gallerystrip", "gallery", "image_strip")),
    LINK_CARD("linkCard", IntelligentUiCategory.MEDIA, setOf("link_card", "linkcard", "link", "url_card")),
    FILE_CARD("fileCard", IntelligentUiCategory.MEDIA, setOf("file_card", "filecard", "file", "attachment_card")),
    CODE_PREVIEW("codePreview", IntelligentUiCategory.MEDIA, setOf("code_preview", "codepreview", "snippet_card")),
    MAP_PIN("mapPin", IntelligentUiCategory.MEDIA, setOf("map_pin", "mappin", "location", "place_card")),

    // 7. Task Semantic (8)
    OPTION_CARD("optionCard", IntelligentUiCategory.TASK_SEMANTIC, setOf("option_card", "optioncard", "option", "decision_option")),
    RECOMMENDATION_HERO("recommendationHero", IntelligentUiCategory.TASK_SEMANTIC, setOf("recommendation_hero", "recommendationhero", "recommendation", "verdict_card")),
    CONSTRAINT_ITEM("constraintItem", IntelligentUiCategory.TASK_SEMANTIC, setOf("constraint_item", "constraintitem", "constraint", "requirement_item")),
    RISK_ITEM("riskItem", IntelligentUiCategory.TASK_SEMANTIC, setOf("risk_item", "riskitem", "risk", "hazard_item")),
    CHECKLIST_ITEM("checklistItem", IntelligentUiCategory.TASK_SEMANTIC, setOf("checklist_item", "checklistitem", "checklist", "todo_item")),
    FLASHCARD("flashcard", IntelligentUiCategory.TASK_SEMANTIC, setOf("flash_card", "study_card", "flip_card")),
    QUIZ_ITEM("quizItem", IntelligentUiCategory.TASK_SEMANTIC, setOf("quiz_item", "quizitem", "quiz", "question", "quiz_question")),
    SCORE_SUMMARY("scoreSummary", IntelligentUiCategory.TASK_SEMANTIC, setOf("score_summary", "scoresummary", "study_summary")),

    // 8. Action & Container (8)
    ACTION_BUTTON("actionButton", IntelligentUiCategory.ACTION_CONTAINER, setOf("action_button", "actionbutton", "button")),
    ACTION_BAR("actionBar", IntelligentUiCategory.ACTION_CONTAINER, setOf("action_bar", "actionbar", "button_row")),
    CONFIRM_BAR("confirmBar", IntelligentUiCategory.ACTION_CONTAINER, setOf("confirm_bar", "confirmbar", "submit_bar")),
    FILTER_BAR("filterBar", IntelligentUiCategory.ACTION_CONTAINER, setOf("filter_bar", "filterbar", "filters")),
    SEARCH_FILTER("searchFilter", IntelligentUiCategory.ACTION_CONTAINER, setOf("search_filter", "searchfilter", "search_box")),
    FOOTNOTE("footnote", IntelligentUiCategory.ACTION_CONTAINER, setOf("note", "disclaimer")),
    SUMMARY_BOX("summaryBox", IntelligentUiCategory.ACTION_CONTAINER, setOf("summary_box", "summarybox", "summary", "takeaway")),
    CTA_CARD("ctaCard", IntelligentUiCategory.ACTION_CONTAINER, setOf("cta_card", "ctacard", "cta", "next_step_card"));

    val isNumericInput: Boolean
        get() = this == NUMBER_FIELD || this == SLIDER || this == STEPPER || this == RATING

    val isInput: Boolean
        get() = category == IntelligentUiCategory.INPUT || this == CHECKLIST_ITEM || this == SEARCH_FILTER || this == FILTER_BAR

    companion object {
        private val lookupMap: Map<String, IntelligentUiComponentKind> = buildMap {
            IntelligentUiComponentKind.entries.forEach { entry ->
                put(normalizeToken(entry.canonicalName), entry)
                put(normalizeToken(entry.name), entry)
                entry.aliases.forEach { alias ->
                    put(normalizeToken(alias), entry)
                }
            }
        }

        private fun normalizeToken(raw: String): String =
            raw.trim()
                .lowercase()
                .replace("-", "")
                .replace("_", "")
                .replace(" ", "")

        fun fromRaw(raw: String?): IntelligentUiComponentKind? {
            if (raw.isNullOrBlank()) return null
            return lookupMap[normalizeToken(raw)]
        }
    }
}

enum class IntelligentUiTone {
    NEUTRAL,
    ACCENT,
    POSITIVE,
    WARNING,
    DANGER,
    INFO;

    companion object {
        fun fromRaw(raw: String?): IntelligentUiTone = when (raw?.trim()?.lowercase()) {
            "accent", "primary", "brand", "highlight" -> ACCENT
            "positive", "success", "good", "green", "ok", "safe" -> POSITIVE
            "warning", "warn", "caution", "amber", "yellow", "medium" -> WARNING
            "danger", "error", "critical", "negative", "red", "high" -> DANGER
            "info", "informative", "blue", "tip" -> INFO
            else -> NEUTRAL
        }
    }
}

enum class IntelligentUiEmphasis {
    SUBTLE,
    NORMAL,
    STRONG,
    HERO;

    companion object {
        fun fromRaw(raw: String?): IntelligentUiEmphasis = when (raw?.trim()?.lowercase()) {
            "subtle", "muted", "low", "secondary", "quiet" -> SUBTLE
            "strong", "high", "bold", "prominent" -> STRONG
            "hero", "display", "featured" -> HERO
            else -> NORMAL
        }
    }
}

enum class IntelligentUiDensity {
    COMPACT,
    COMFORTABLE,
    SPACIOUS;

    companion object {
        fun fromRaw(raw: String?): IntelligentUiDensity = when (raw?.trim()?.lowercase()) {
            "compact", "tight", "dense", "sm", "small" -> COMPACT
            "spacious", "relaxed", "loose", "lg", "large" -> SPACIOUS
            else -> COMFORTABLE
        }
    }
}

enum class IntelligentUiAlignment {
    START,
    CENTER,
    END;

    companion object {
        fun fromRaw(raw: String?): IntelligentUiAlignment = when (raw?.trim()?.lowercase()) {
            "center", "middle", "centered" -> CENTER
            "end", "right", "trailing" -> END
            else -> START
        }
    }
}

enum class IntelligentUiSizeToken {
    NONE,
    XS,
    SM,
    MD,
    LG;

    companion object {
        fun fromRaw(raw: String?, default: IntelligentUiSizeToken = MD): IntelligentUiSizeToken =
            when (raw?.trim()?.lowercase()) {
                "none", "0", "zero" -> NONE
                "xs", "extra_small", "tiny", "4" -> XS
                "sm", "small", "compact", "8" -> SM
                "md", "medium", "normal", "default", "12" -> MD
                "lg", "large", "spacious", "16", "20" -> LG
                else -> default
            }
    }
}

data class IntelligentUiStyle(
    val tone: IntelligentUiTone = IntelligentUiTone.NEUTRAL,
    val emphasis: IntelligentUiEmphasis = IntelligentUiEmphasis.NORMAL,
    val density: IntelligentUiDensity = IntelligentUiDensity.COMFORTABLE,
    val alignment: IntelligentUiAlignment = IntelligentUiAlignment.START,
    val spacing: IntelligentUiSizeToken = IntelligentUiSizeToken.MD,
    val padding: IntelligentUiSizeToken = IntelligentUiSizeToken.MD,
) {
    val spacingDp: Int?
        get() = when (spacing) {
            IntelligentUiSizeToken.NONE -> 0
            IntelligentUiSizeToken.XS -> 4
            IntelligentUiSizeToken.SM -> 6
            IntelligentUiSizeToken.MD -> null
            IntelligentUiSizeToken.LG -> 14
        }

    val paddingDp: Int?
        get() = when (padding) {
            IntelligentUiSizeToken.NONE -> 0
            IntelligentUiSizeToken.XS -> 6
            IntelligentUiSizeToken.SM -> 9
            IntelligentUiSizeToken.MD -> null
            IntelligentUiSizeToken.LG -> 16
        }
}

data class IntelligentUiKeyValue(
    val key: String,
    val value: String,
    val hint: String? = null,
    val valueFieldId: String? = null,
    val unit: String? = null,
    val tone: IntelligentUiTone = IntelligentUiTone.NEUTRAL,
)

data class IntelligentUiOptionItem(
    val id: String,
    val label: String,
    val value: String = label,
    val subtitle: String? = null,
    val description: String? = null,
    val badge: String? = null,
    val pros: List<String> = emptyList(),
    val cons: List<String> = emptyList(),
    val metrics: List<IntelligentUiKeyValue> = emptyList(),
    val recommended: Boolean = false,
    val tone: IntelligentUiTone = IntelligentUiTone.NEUTRAL,
)

data class IntelligentUiDataPoint(
    val id: String = "",
    val label: String,
    val value: Double,
    val max: Double? = null,
    val unit: String? = null,
    val delta: String? = null,
    val valueFieldId: String? = null,
    val scale: Double = 1.0,
    val offset: Double = 0.0,
    val tone: IntelligentUiTone = IntelligentUiTone.NEUTRAL,
)

data class IntelligentUiTabItem(
    val id: String,
    val title: String,
    val badge: String? = null,
    val children: List<IntelligentUiComponentNode> = emptyList(),
) {
    val label: String
        get() = title
}

data class IntelligentUiComponentNode(
    val id: String,
    val kind: IntelligentUiComponentKind,
    val title: String? = null,
    val subtitle: String? = null,
    val text: String? = null,
    val badge: String? = null,
    val icon: String? = null,
    val url: String? = null,
    val code: String? = null,
    val language: String? = null,
    val placeholder: String? = null,
    val hint: String? = null,
    val explanation: String? = null,
    val unit: String? = null,
    val prefix: String? = null,
    val suffix: String? = null,
    val required: Boolean = false,
    val defaultValue: String? = null,
    val numericValue: Double? = null,
    val min: Double? = null,
    val max: Double? = null,
    val step: Double? = null,
    val scale: Double = 1.0,
    val offset: Double = 0.0,
    val valueFieldId: String? = null,
    val recommended: Boolean = false,
    val checked: Boolean? = null,
    val expandedByDefault: Boolean = false,
    val columnsCount: Int = 2,
    val options: List<IntelligentUiOptionItem> = emptyList(),
    val pros: List<String> = emptyList(),
    val cons: List<String> = emptyList(),
    val items: List<String> = emptyList(),
    val keyValues: List<IntelligentUiKeyValue> = emptyList(),
    val tableHeaders: List<String> = emptyList(),
    val tableRows: List<List<String>> = emptyList(),
    val points: List<IntelligentUiDataPoint> = emptyList(),
    val tabs: List<IntelligentUiTabItem> = emptyList(),
    val children: List<IntelligentUiComponentNode> = emptyList(),
    val secondaryChildren: List<IntelligentUiComponentNode> = emptyList(),
    val correctOption: String? = null,
    val correctIndex: Int? = null,
    val difficulty: String? = null,
    val actionInstruction: String? = null,
    val style: IntelligentUiStyle = IntelligentUiStyle(),
) {
    val body: String? get() = text
    val front: String? get() = title
    val back: String? get() = text
    val valueText: String? get() = text
    val columns: Int? get() = columnsCount
    val headers: List<String> get() = tableHeaders
    val rows: List<List<String>> get() = tableRows
    val dataPoints: List<IntelligentUiDataPoint> get() = points
    val delta: String? get() = hint
    val leftLabel: String? get() = keyValues.getOrNull(0)?.key
    val rightLabel: String? get() = keyValues.getOrNull(0)?.value
    val imageUrl: String? get() = url
    val actionLabel: String? get() = title
    val actionPrompt: String? get() = actionInstruction
}

data class IntelligentUiDocument(
    val id: String,
    val kind: TaskUiKind,
    val title: String,
    val subtitle: String? = null,
    val summary: String? = null,
    val badge: String? = null,
    val style: IntelligentUiStyle = IntelligentUiStyle(),
    val options: List<IntelligentUiOptionItem> = emptyList(),
    val components: List<IntelligentUiComponentNode> = emptyList(),
    val submitLabel: String = "确认发送",
    val resetLabel: String = "重置",
    val promptTemplate: String? = null,
    val actions: List<GenerativeWidgetAction> = emptyList(),
) {
    /**
     * Flattens all component nodes in tree order (including tab children and split secondary children).
     */
    fun allNodes(): List<IntelligentUiComponentNode> {
        val result = ArrayList<IntelligentUiComponentNode>()
        fun visit(node: IntelligentUiComponentNode) {
            result += node
            node.children.forEach(::visit)
            node.secondaryChildren.forEach(::visit)
            node.tabs.forEach { tab -> tab.children.forEach(::visit) }
        }
        components.forEach(::visit)
        return result
    }

    fun inputNodes(): List<IntelligentUiComponentNode> =
        allNodes().filter { it.kind.isInput }

    fun flashcardNodes(): List<IntelligentUiComponentNode> =
        allNodes().filter { it.kind == IntelligentUiComponentKind.FLASHCARD }

    fun quizNodes(): List<IntelligentUiComponentNode> =
        allNodes().filter { it.kind == IntelligentUiComponentKind.QUIZ_ITEM }
}

/**
 * Lightweight streaming preview extracted while a `task-ui` fence is still streaming.
 */
data class IntelligentUiStreamingPreview(
    val kind: TaskUiKind?,
    val title: String?,
    val subtitle: String?,
    val detectedComponents: List<IntelligentUiComponentKind> = emptyList(),
    val itemCount: Int = 0,
)

enum class FlashcardMastery(val label: String) {
    UNREVIEWED("未标记"),
    MASTERED("已掌握"),
    REVIEW_AGAIN("需复习");

    companion object {
        val UNSEEN: FlashcardMastery get() = UNREVIEWED
        val REVIEW: FlashcardMastery get() = REVIEW_AGAIN
    }
}

/**
 * Interactive state for a single `IntelligentUiDocument` instance, preserved across LazyColumn scroll
 * and recomposition via [IntelligentUiStateStore].
 */
@Stable
class IntelligentUiCardState(
    val cardKey: String,
    document: IntelligentUiDocument,
) {
    constructor(document: IntelligentUiDocument) : this(cardKey = document.id, document = document)
    private var boundDocumentId: String = document.id

    var selectedDecisionOptionId by mutableStateOf<String?>(null)
    val fieldValues = mutableStateMapOf<String, String>()
    val multiFieldValues = mutableStateMapOf<String, Set<String>>()
    val activeTabByNodeId = mutableStateMapOf<String, String>()
    val expandedByNodeId = mutableStateMapOf<String, Boolean>()

    // Study state
    var currentFlashcardIndex by mutableStateOf(0)
    val flippedFlashcards = mutableStateMapOf<String, Boolean>()
    val flashcardMastery = mutableStateMapOf<String, FlashcardMastery>()
    val quizSelectedOption = mutableStateMapOf<String, String>()
    var retryWrongOnly by mutableStateOf(false)
    val retryWrongQuestionIds = mutableStateMapOf<String, Boolean>()

    // Submission state
    var submittedSummary by mutableStateOf<String?>(null)

    init {
        seedDefaults(document, overwriteExisting = true)
    }

    fun syncWithDocument(document: IntelligentUiDocument) {
        val changedDoc = boundDocumentId != document.id
        boundDocumentId = document.id
        seedDefaults(document, overwriteExisting = changedDoc)
    }

    fun seedDefaults(document: IntelligentUiDocument, overwriteExisting: Boolean = false) {
        val recommendedOption = document.options.firstOrNull { it.recommended }
            ?: document.allNodes()
                .firstOrNull { it.kind == IntelligentUiComponentKind.OPTION_CARD && it.recommended }
                ?.let { IntelligentUiOptionItem(id = it.id, label = it.title ?: it.id) }
        if (overwriteExisting || selectedDecisionOptionId == null) {
            selectedDecisionOptionId = recommendedOption?.id ?: document.options.firstOrNull()?.id
        }

        document.allNodes().forEach { node ->
            when (node.kind) {
                IntelligentUiComponentKind.TABS -> {
                    val firstTabId = node.tabs.firstOrNull()?.id
                    if (firstTabId != null && (overwriteExisting || !activeTabByNodeId.containsKey(node.id))) {
                        activeTabByNodeId[node.id] = firstTabId
                    }
                }
                IntelligentUiComponentKind.ACCORDION,
                IntelligentUiComponentKind.DISCLOSURE -> {
                    if (overwriteExisting || !expandedByNodeId.containsKey(node.id)) {
                        expandedByNodeId[node.id] = node.expandedByDefault
                    }
                }
                IntelligentUiComponentKind.MULTI_SELECT,
                IntelligentUiComponentKind.CHIP_GROUP -> {
                    if (overwriteExisting || !multiFieldValues.containsKey(node.id)) {
                        val initialSet = node.defaultValue
                            ?.split(',')
                            ?.map { it.trim() }
                            ?.filter { it.isNotBlank() }
                            ?.toSet()
                            .orEmpty()
                        multiFieldValues[node.id] = initialSet
                    }
                }
                IntelligentUiComponentKind.TOGGLE,
                IntelligentUiComponentKind.CHECKBOX,
                IntelligentUiComponentKind.CHECKLIST_ITEM -> {
                    if (overwriteExisting || !fieldValues.containsKey(node.id)) {
                        val initialBool = node.checked
                            ?: node.defaultValue?.lowercase()?.let { it == "true" || it == "1" || it == "yes" }
                            ?: false
                        fieldValues[node.id] = initialBool.toString()
                    }
                }
                IntelligentUiComponentKind.NUMBER_FIELD,
                IntelligentUiComponentKind.SLIDER,
                IntelligentUiComponentKind.STEPPER,
                IntelligentUiComponentKind.RATING -> {
                    if (overwriteExisting || !fieldValues.containsKey(node.id)) {
                        val initialNumber = node.numericValue
                            ?: node.defaultValue?.toDoubleOrNull()
                            ?: node.min
                            ?: if (node.kind == IntelligentUiComponentKind.RATING) 4.0 else 0.0
                        fieldValues[node.id] = formatNumber(initialNumber)
                    }
                }
                else -> {
                    if (node.kind.isInput && (overwriteExisting || !fieldValues.containsKey(node.id))) {
                        val initial = node.defaultValue
                            ?: node.options.firstOrNull { it.recommended }?.value
                            ?: if (node.kind == IntelligentUiComponentKind.RADIO_GROUP ||
                                node.kind == IntelligentUiComponentKind.SEGMENTED_CONTROL ||
                                node.kind == IntelligentUiComponentKind.SELECT
                            ) {
                                node.options.firstOrNull()?.value.orEmpty()
                            } else {
                                ""
                            }
                        fieldValues[node.id] = initial
                    }
                }
            }
        }
    }

    fun reset(document: IntelligentUiDocument) {
        selectedDecisionOptionId = null
        fieldValues.clear()
        multiFieldValues.clear()
        flippedFlashcards.clear()
        flashcardMastery.clear()
        quizSelectedOption.clear()
        retryWrongOnly = false
        retryWrongQuestionIds.clear()
        currentFlashcardIndex = 0
        submittedSummary = null
        seedDefaults(document, overwriteExisting = true)
    }

    fun setFieldValue(fieldId: String, value: String) {
        fieldValues[fieldId] = value
        submittedSummary = null
    }

    fun setNumericFieldValue(fieldId: String, value: Double, min: Double? = null, max: Double? = null, step: Double? = null) {
        var clamped = value
        if (min != null) clamped = clamped.coerceAtLeast(min)
        if (max != null) clamped = clamped.coerceAtMost(max)
        if (step != null && step > 0.0) {
            val base = min ?: 0.0
            val steps = ((clamped - base) / step).roundToInt()
            clamped = base + steps * step
            if (min != null) clamped = clamped.coerceAtLeast(min)
            if (max != null) clamped = clamped.coerceAtMost(max)
        }
        fieldValues[fieldId] = formatNumber(clamped)
        submittedSummary = null
    }

    fun toggleMultiOption(fieldId: String, optionValue: String) {
        val current = multiFieldValues[fieldId].orEmpty().toMutableSet()
        if (!current.add(optionValue)) {
            current.remove(optionValue)
        }
        multiFieldValues[fieldId] = current
        submittedSummary = null
    }

    /**
     * Resolves a bound numeric value for `value_field_id` (`valueFieldId`) across the document.
     */
    fun boundNumericValue(
        document: IntelligentUiDocument,
        valueFieldId: String?,
        fallback: Double? = null,
        scale: Double = 1.0,
        offset: Double = 0.0,
    ): Double? {
        if (valueFieldId.isNullOrBlank()) return fallback
        val rawText = fieldValues[valueFieldId]?.trim()
        val parsed = rawText?.toDoubleOrNull()
            ?: document.allNodes().firstOrNull { it.id == valueFieldId }?.let { source ->
                source.numericValue ?: source.defaultValue?.toDoubleOrNull() ?: source.min
            }
            ?: fallback
            ?: return null
        return parsed * scale + offset
    }

    /**
     * Validates a single input node; returns a non-null human-readable error message when invalid.
     */
    fun validationErrorFor(node: IntelligentUiComponentNode): String? {
        if (!node.kind.isInput) return null
        val label = node.title?.takeIf { it.isNotBlank() } ?: node.id
        return when {
            node.kind == IntelligentUiComponentKind.MULTI_SELECT ||
                node.kind == IntelligentUiComponentKind.CHIP_GROUP -> {
                val selected = multiFieldValues[node.id].orEmpty()
                if (node.required && selected.isEmpty()) "请选择「$label」" else null
            }
            node.kind.isNumericInput -> {
                val raw = fieldValues[node.id]?.trim().orEmpty()
                if (raw.isEmpty()) {
                    return if (node.required) "请填写「$label」" else null
                }
                val num = raw.toDoubleOrNull()
                if (num == null || !num.isFinite()) {
                    return "「$label」请输入有效数字"
                }
                val min = node.min
                val max = node.max
                if (min != null && num < min - 1e-9) {
                    return "「$label」不能小于 ${formatNumber(min)}${node.unit.orEmpty()}"
                }
                if (max != null && num > max + 1e-9) {
                    return "「$label」不能大于 ${formatNumber(max)}${node.unit.orEmpty()}"
                }
                val step = node.step
                if (step != null && step > 0.0 && node.kind == IntelligentUiComponentKind.NUMBER_FIELD) {
                    val base = min ?: 0.0
                    val quotient = (num - base) / step
                    if (abs(quotient - quotient.roundToInt()) > 1e-5) {
                        return "「$label」步进需为 ${formatNumber(step)} 的倍数"
                    }
                }
                null
            }
            node.kind == IntelligentUiComponentKind.TOGGLE ||
                node.kind == IntelligentUiComponentKind.CHECKBOX ||
                node.kind == IntelligentUiComponentKind.CHECKLIST_ITEM -> {
                val checked = fieldValues[node.id] == "true"
                if (node.required && !checked) "请确认「$label」" else null
            }
            else -> {
                val value = fieldValues[node.id]?.trim().orEmpty()
                if (node.required && value.isEmpty()) "请填写「$label」" else null
            }
        }
    }

    /**
     * Returns all validation errors across the document's input nodes (and decision selection).
     */
    fun validationErrors(document: IntelligentUiDocument): List<String> {
        val errors = ArrayList<String>()
        if (document.kind == TaskUiKind.DECISION) {
            val allDecisionOptions = buildList {
                addAll(document.options)
                document.allNodes()
                    .filter { it.kind == IntelligentUiComponentKind.OPTION_CARD }
                    .forEach { node ->
                        add(IntelligentUiOptionItem(id = node.id, label = node.title ?: node.id))
                    }
            }
            if (allDecisionOptions.isNotEmpty() && selectedDecisionOptionId.isNullOrBlank()) {
                errors += "请先选择一个方案"
            }
        }
        document.inputNodes().forEach { node ->
            validationErrorFor(node)?.let(errors::add)
        }
        return errors
    }

    // Study helpers
    fun isQuizQuestionAnswered(questionNode: IntelligentUiComponentNode): Boolean =
        !quizSelectedOption[questionNode.id].isNullOrBlank()

    fun isQuizQuestionCorrect(questionNode: IntelligentUiComponentNode): Boolean {
        val selected = quizSelectedOption[questionNode.id]?.trim() ?: return false
        val correctIndex = questionNode.correctIndex
        if (correctIndex != null && correctIndex in questionNode.options.indices) {
            val expectedOption = questionNode.options[correctIndex]
            if (selected.equals(expectedOption.id, ignoreCase = true) ||
                selected.equals(expectedOption.value, ignoreCase = true) ||
                selected.equals(expectedOption.label, ignoreCase = true)
            ) {
                return true
            }
        }
        val expected = questionNode.correctOption?.trim() ?: return false
        if (selected.equals(expected, ignoreCase = true)) return true
        val matchedOption = questionNode.options.firstOrNull { opt ->
            opt.id.equals(selected, ignoreCase = true) ||
                opt.value.equals(selected, ignoreCase = true) ||
                opt.label.equals(selected, ignoreCase = true)
        }
        return matchedOption != null && (
            matchedOption.id.equals(expected, ignoreCase = true) ||
                matchedOption.value.equals(expected, ignoreCase = true) ||
                matchedOption.label.equals(expected, ignoreCase = true)
            )
    }

    fun startRetryWrongQuestions(quizNodes: List<IntelligentUiComponentNode>) {
        val wrongIds = quizNodes
            .filter { isQuizQuestionAnswered(it) && !isQuizQuestionCorrect(it) }
            .map { it.id }
        if (wrongIds.isEmpty()) return
        retryWrongQuestionIds.clear()
        wrongIds.forEach { id ->
            retryWrongQuestionIds[id] = true
            quizSelectedOption.remove(id)
        }
        retryWrongOnly = true
    }

    fun exitRetryWrongQuestions() {
        retryWrongOnly = false
        retryWrongQuestionIds.clear()
    }

    /**
     * Builds a clean user prompt representing the user's selections/inputs/study results
     * to send back into the conversation via `onGenerativeWidgetAction`.
     */
    fun buildSubmissionPrompt(document: IntelligentUiDocument): String {
        val lines = ArrayList<String>()
        val header = "【${document.kind.displayLabel} · ${document.title}】"
        lines += header

        when (document.kind) {
            TaskUiKind.DECISION -> {
                val optionPool = buildList {
                    addAll(document.options)
                    document.allNodes()
                        .filter { it.kind == IntelligentUiComponentKind.OPTION_CARD }
                        .forEach { node ->
                            if (none { it.id == node.id }) {
                                add(
                                    IntelligentUiOptionItem(
                                        id = node.id,
                                        label = node.title ?: node.id,
                                        subtitle = node.subtitle,
                                    )
                                )
                            }
                        }
                }
                val chosen = optionPool.firstOrNull { it.id == selectedDecisionOptionId }
                if (chosen != null) {
                    lines += "- 已选方案：${chosen.label}${chosen.subtitle?.let { "（$it）" }.orEmpty()}"
                }
                appendInputSummaryLines(document, lines)
            }
            TaskUiKind.FORM,
            TaskUiKind.EXPLORE -> {
                appendInputSummaryLines(document, lines)
                // Also include any bound metric/progress snapshots so the model sees computed results
                document.allNodes()
                    .filter {
                        (it.kind == IntelligentUiComponentKind.METRIC ||
                            it.kind == IntelligentUiComponentKind.PROGRESS ||
                            it.kind == IntelligentUiComponentKind.STEP_PROGRESS) &&
                            !it.valueFieldId.isNullOrBlank()
                    }
                    .forEach { metricNode ->
                        val resolved = boundNumericValue(
                            document = document,
                            valueFieldId = metricNode.valueFieldId,
                            fallback = metricNode.numericValue,
                            scale = metricNode.scale,
                            offset = metricNode.offset,
                        )
                        if (resolved != null) {
                            val label = metricNode.title ?: metricNode.id
                            lines += "- 联动指标「$label」：${formatNumber(resolved)}${metricNode.unit.orEmpty()}"
                        }
                    }
            }
            TaskUiKind.STUDY -> {
                val flashcards = document.flashcardNodes()
                if (flashcards.isNotEmpty()) {
                    val mastered = flashcards.count { flashcardMastery[it.id] == FlashcardMastery.MASTERED }
                    val review = flashcards.count { flashcardMastery[it.id] == FlashcardMastery.REVIEW_AGAIN }
                    lines += "- 闪卡复习：已掌握 $mastered/${flashcards.size} 张，待重练 $review 张"
                }
                val quizzes = document.quizNodes()
                if (quizzes.isNotEmpty()) {
                    val answered = quizzes.filter { isQuizQuestionAnswered(it) }
                    val correct = answered.count { isQuizQuestionCorrect(it) }
                    val wrong = answered.filterNot { isQuizQuestionCorrect(it) }
                    lines += "- 测验成绩：已答 ${answered.size}/${quizzes.size} 题，答对 $correct/${quizzes.size} 题，答错 ${wrong.size} 题"
                    if (wrong.isNotEmpty()) {
                        val wrongTitles = wrong.mapNotNull { it.title?.takeIf(String::isNotBlank) }.take(5)
                        if (wrongTitles.isNotEmpty()) {
                            lines += "- 错题列表：${wrongTitles.joinToString("；")}"
                        }
                    }
                }
            }
        }

        document.promptTemplate?.takeIf { it.isNotBlank() }?.let { instruction ->
            lines += "请根据以上结果继续：$instruction"
        }
        return lines.joinToString("\n")
    }

    private fun appendInputSummaryLines(
        document: IntelligentUiDocument,
        lines: MutableList<String>,
    ) {
        document.inputNodes().forEach { node ->
            val label = node.title?.takeIf { it.isNotBlank() } ?: node.id
            when (node.kind) {
                IntelligentUiComponentKind.MULTI_SELECT,
                IntelligentUiComponentKind.CHIP_GROUP -> {
                    val selected = multiFieldValues[node.id].orEmpty()
                    if (selected.isNotEmpty()) {
                        val displayLabels = selected.map { sel ->
                            node.options.firstOrNull { it.value == sel || it.id == sel }?.label ?: sel
                        }
                        lines += "- $label：${displayLabels.joinToString("、")}"
                    }
                }
                IntelligentUiComponentKind.TOGGLE,
                IntelligentUiComponentKind.CHECKBOX,
                IntelligentUiComponentKind.CHECKLIST_ITEM -> {
                    val checked = fieldValues[node.id] == "true"
                    lines += "- $label：${if (checked) "是" else "否"}"
                }
                else -> {
                    val raw = fieldValues[node.id]?.trim().orEmpty()
                    if (raw.isNotEmpty()) {
                        val display = node.options.firstOrNull { it.value == raw || it.id == raw }?.label
                            ?: "$raw${node.unit.orEmpty()}"
                        lines += "- $label：$display"
                    }
                }
            }
        }
    }

    companion object {
        fun formatNumber(value: Double): String {
            if (!value.isFinite()) return "0"
            val roundedInt = value.roundToInt()
            return if (abs(value - roundedInt) < 1e-6) {
                roundedInt.toString()
            } else {
                "%.2f".format(java.util.Locale.US, value).trimEnd('0').trimEnd('.')
            }
        }
    }
}

/**
 * Process-lifetime store for `task-ui` card states so LazyColumn recycling never loses
 * user edits, active tabs, or study quiz progress.
 */
object IntelligentUiStateStore {
    private val states = ConcurrentHashMap<String, IntelligentUiCardState>()

    fun getOrCreate(cardKey: String, document: IntelligentUiDocument): IntelligentUiCardState {
        return states.computeIfAbsent(cardKey) {
            IntelligentUiCardState(cardKey = cardKey, document = document)
        }.also { state ->
            state.syncWithDocument(document)
        }
    }

    fun stateFor(cardKey: String, document: IntelligentUiDocument): IntelligentUiCardState =
        getOrCreate(cardKey, document)

    fun peek(cardKey: String): IntelligentUiCardState? = states[cardKey]

    internal fun clearForTesting() {
        states.clear()
    }

    internal fun clearAll() {
        states.clear()
    }
}

/**
 * Parser for `task-ui` / `intelligent-ui` / `adaptive-ui` JSON blocks (complete and streaming).
 */
object IntelligentUiParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val componentKeyRegex = Regex(
        """"(?:type|kind|component)"\s*:\s*"([^"]+)"""",
        RegexOption.IGNORE_CASE,
    )

    fun parseDocument(
        jsonText: String,
        copy: GenerativeWidgetCopy = GenerativeWidgetCopy.DEFAULT,
    ): IntelligentUiDocument? {
        val root = runCatching { json.parseToJsonElement(jsonText) as? JsonObject }.getOrNull()
            ?: return null
        val payload = (root["spec"] as? JsonObject) ?: root

        val explicitKind = TaskUiKind.fromRaw(
            payload.stringOrNull("kind")
                ?: payload.stringOrNull("task_type")
                ?: payload.stringOrNull("taskType")
                ?: payload.stringOrNull("mode")
                ?: payload.stringOrNull("type")
                ?: root.stringOrNull("kind")
                ?: root.stringOrNull("task_type")
                ?: root.stringOrNull("type")
        )

        val style = parseStyle(payload["style"] as? JsonObject ?: payload)
        val topOptions = parseOptionList(payload["options"] ?: payload["choices"])

        val nodes = ArrayList<IntelligentUiComponentNode>()
        var nodeCounter = 0
        fun nextNodeId(prefix: String): String = "${prefix}_${++nodeCounter}"

        // 1. Top-level flashcards (for `study`)
        val flashcardsElement = payload["flashcards"]
            ?: payload["cards"]
            ?: (payload["study"] as? JsonObject)?.get("flashcards")
            ?: (payload["study"] as? JsonObject)?.get("cards")
        (flashcardsElement as? JsonArray)?.forEach { item ->
            parseFlashcardElement(item, ::nextNodeId)?.let(nodes::add)
        }

        // 2. Top-level quiz questions (for `study`)
        val quizElement = payload["quiz"]
            ?: payload["questions"]
            ?: (payload["study"] as? JsonObject)?.get("quiz")
            ?: (payload["study"] as? JsonObject)?.get("questions")
        when (quizElement) {
            is JsonArray -> quizElement.forEach { item ->
                parseQuizElement(item, ::nextNodeId)?.let(nodes::add)
            }
            is JsonObject -> {
                (quizElement["questions"] as? JsonArray ?: quizElement["items"] as? JsonArray)?.forEach { item ->
                    parseQuizElement(item, ::nextNodeId)?.let(nodes::add)
                }
            }
            else -> Unit
        }

        // 3. Top-level form fields
        (payload["fields"] as? JsonArray)?.forEach { fieldEl ->
            parseComponentNode(fieldEl, ::nextNodeId, defaultKind = IntelligentUiComponentKind.TEXT_FIELD)?.let(nodes::add)
        }

        // 4. Top-level tabs
        val topTabs = parseTabsArray(payload["tabs"] as? JsonArray, ::nextNodeId)
        if (topTabs.isNotEmpty()) {
            nodes += IntelligentUiComponentNode(
                id = nextNodeId("tabs"),
                kind = IntelligentUiComponentKind.TABS,
                tabs = topTabs,
            )
        }

        // 5. Top-level sections / components / body / items / children
        listOf("sections", "components", "body", "children").forEach { key ->
            (payload[key] as? JsonArray)?.forEach { childEl ->
                parseComponentNode(childEl, ::nextNodeId)?.let(nodes::add)
            }
        }
        if (nodes.isEmpty() && topOptions.isEmpty()) {
            (payload["items"] as? JsonArray)?.forEach { childEl ->
                parseComponentNode(childEl, ::nextNodeId)?.let(nodes::add)
            }
        }

        val inferredKind = explicitKind ?: inferTaskKind(payload, topOptions, nodes)
        val rawTitle = payload.stringOrNull("title")
            ?: root.stringOrNull("title")
            ?: payload.stringOrNull("heading")
            ?: payload.stringOrNull("question")
            ?: inferredKind.displayLabel
        val subtitle = payload.stringOrNull("subtitle")
            ?: payload.stringOrNull("description")
            ?: root.stringOrNull("subtitle")
        val summary = payload.stringOrNull("summary")
            ?: root.stringOrNull("summary")
        val badge = payload.stringOrNull("badge")
            ?: payload.stringOrNull("eyebrow")
            ?: payload.stringOrNull("category")

        // Must have at least one option or one component node to be a valid task-ui document
        if (topOptions.isEmpty() && nodes.isEmpty()) return null

        val docId = payload.stringOrNull("id")
            ?: root.stringOrNull("id")
            ?: "${inferredKind.wireName}_${rawTitle.hashCode()}_${nodes.size}_${topOptions.size}"

        val defaultSubmitLabel = when (inferredKind) {
            TaskUiKind.DECISION -> "确认方案"
            TaskUiKind.FORM -> "提交信息"
            TaskUiKind.EXPLORE -> "应用参数"
            TaskUiKind.STUDY -> "发送学习结果"
        }

        return IntelligentUiDocument(
            id = docId,
            kind = inferredKind,
            title = rawTitle.trim(),
            subtitle = subtitle?.trim()?.takeIf { it.isNotBlank() },
            summary = summary?.trim()?.takeIf { it.isNotBlank() },
            badge = badge?.trim()?.takeIf { it.isNotBlank() },
            style = style,
            options = topOptions,
            components = nodes,
            submitLabel = (
                payload.stringOrNull("submit_label")
                    ?: payload.stringOrNull("submitLabel")
                    ?: payload.stringOrNull("confirm_label")
                    ?: payload.stringOrNull("confirmLabel")
                )?.trim()?.takeIf { it.isNotBlank() } ?: defaultSubmitLabel,
            resetLabel = (
                payload.stringOrNull("reset_label")
                    ?: payload.stringOrNull("resetLabel")
                )?.trim()?.takeIf { it.isNotBlank() } ?: "重置",
            promptTemplate = (
                payload.stringOrNull("prompt_template")
                    ?: payload.stringOrNull("promptTemplate")
                    ?: payload.stringOrNull("instruction")
                )?.trim()?.takeIf { it.isNotBlank() },
            actions = parseActions(payload["actions"] ?: root["actions"], copy),
        )
    }

    /**
     * Parses a lightweight streaming preview from an incomplete JSON payload.
     */
    fun parseStreamingPreview(partialJson: String): IntelligentUiStreamingPreview {
        val kindRaw = extractJsonString(partialJson, "kind")
            ?: extractJsonString(partialJson, "task_type")
            ?: extractJsonString(partialJson, "taskType")
            ?: extractJsonString(partialJson, "mode")
        val kind = TaskUiKind.fromRaw(kindRaw) ?: when {
            "\"flashcards\"" in partialJson || "\"quiz\"" in partialJson || "\"flashcard\"" in partialJson -> TaskUiKind.STUDY
            "\"fields\"" in partialJson -> TaskUiKind.FORM
            "\"pros\"" in partialJson || "\"cons\"" in partialJson -> TaskUiKind.DECISION
            "\"tabs\"" in partialJson -> TaskUiKind.EXPLORE
            else -> null
        }
        val title = extractJsonString(partialJson, "title")
            ?: extractJsonString(partialJson, "heading")
        val subtitle = extractJsonString(partialJson, "subtitle")
            ?: extractJsonString(partialJson, "description")
            ?: extractJsonString(partialJson, "summary")

        val detectedKinds = componentKeyRegex.findAll(partialJson)
            .mapNotNull { match -> IntelligentUiComponentKind.fromRaw(match.groupValues[1]) }
            .distinct()
            .take(8)
            .toList()
        val itemCount = Regex(""""(?:id|question|front|label)"\s*:""").findAll(partialJson).count()

        return IntelligentUiStreamingPreview(
            kind = kind,
            title = title?.trim()?.takeIf { it.isNotBlank() },
            subtitle = subtitle?.trim()?.takeIf { it.isNotBlank() },
            detectedComponents = detectedKinds,
            itemCount = itemCount,
        )
    }

    private fun inferTaskKind(
        payload: JsonObject,
        topOptions: List<IntelligentUiOptionItem>,
        nodes: List<IntelligentUiComponentNode>,
    ): TaskUiKind {
        val allKinds = buildList {
            fun collect(node: IntelligentUiComponentNode) {
                add(node.kind)
                node.children.forEach(::collect)
                node.secondaryChildren.forEach(::collect)
                node.tabs.forEach { t -> t.children.forEach(::collect) }
            }
            nodes.forEach(::collect)
        }
        return when {
            allKinds.any {
                it == IntelligentUiComponentKind.FLASHCARD ||
                    it == IntelligentUiComponentKind.QUIZ_ITEM ||
                    it == IntelligentUiComponentKind.SCORE_SUMMARY
            } -> TaskUiKind.STUDY
            topOptions.isNotEmpty() || allKinds.any {
                it == IntelligentUiComponentKind.OPTION_CARD ||
                    it == IntelligentUiComponentKind.RECOMMENDATION_HERO
            } -> TaskUiKind.DECISION
            payload.containsKey("fields") || (
                allKinds.any { it.category == IntelligentUiCategory.INPUT } &&
                    allKinds.none {
                        it == IntelligentUiComponentKind.TABS ||
                            it == IntelligentUiComponentKind.BAR_CHART ||
                            it == IntelligentUiComponentKind.LINE_CHART
                    }
                ) -> TaskUiKind.FORM
            else -> TaskUiKind.EXPLORE
        }
    }

    private fun parseComponentNode(
        element: JsonElement?,
        nextId: (String) -> String,
        defaultKind: IntelligentUiComponentKind? = null,
    ): IntelligentUiComponentNode? {
        if (element == null) return null
        if (element is JsonPrimitive) {
            val content = element.contentOrNull?.trim()?.takeIf { it.isNotBlank() } ?: return null
            return IntelligentUiComponentNode(
                id = nextId("text"),
                kind = defaultKind ?: IntelligentUiComponentKind.TEXT,
                text = content,
            )
        }
        val obj = element as? JsonObject ?: return null
        val rawKind = obj.stringOrNull("kind")
            ?: obj.stringOrNull("type")
            ?: obj.stringOrNull("component")
        val kind = IntelligentUiComponentKind.fromRaw(rawKind)
            ?: defaultKind
            ?: inferNodeKindFromShape(obj)
            ?: return null

        val id = obj.stringOrNull("id")
            ?: obj.stringOrNull("field_id")
            ?: obj.stringOrNull("fieldId")
            ?: obj.stringOrNull("key")
            ?: obj.stringOrNull("name")
            ?: nextId(kind.canonicalName)

        val style = parseStyle(obj["style"] as? JsonObject ?: obj)
        val title = obj.stringOrNull("title")
            ?: obj.stringOrNull("label")
            ?: obj.stringOrNull("heading")
            ?: obj.stringOrNull("question")
            ?: obj.stringOrNull("front")
            ?: obj.stringOrNull("term")
        val subtitle = obj.stringOrNull("subtitle")
            ?: obj.stringOrNull("sublabel")
            ?: obj.stringOrNull("role")
            ?: obj.stringOrNull("date")
            ?: obj.stringOrNull("time")
        val text = obj.stringOrNull("text")
            ?: obj.stringOrNull("body")
            ?: obj.stringOrNull("content")
            ?: obj.stringOrNull("description")
            ?: obj.stringOrNull("back")
            ?: obj.stringOrNull("definition")
            ?: (if (!kind.isInput && kind != IntelligentUiComponentKind.METRIC && kind != IntelligentUiComponentKind.PROGRESS) {
                obj.stringOrNull("value")
            } else null)

        val valueFieldId = obj.stringOrNull("value_field_id")
            ?: obj.stringOrNull("valueFieldId")
            ?: obj.stringOrNull("bind_field_id")
            ?: obj.stringOrNull("bindFieldId")

        val numericVal = obj.doubleOrNull("value")
            ?: obj.doubleOrNull("numeric_value")
            ?: obj.doubleOrNull("numericValue")
            ?: obj.doubleOrNull("current")
            ?: obj.doubleOrNull("score")
            ?: obj.doubleOrNull("progress")

        val defaultVal = obj.stringOrNull("default_value")
            ?: obj.stringOrNull("defaultValue")
            ?: obj.stringOrNull("initial_value")
            ?: obj.stringOrNull("initialValue")
            ?: (if (kind.isInput) obj.stringOrNull("value") else null)

        val options = parseOptionList(obj["options"] ?: obj["choices"])
        val pros = parseStringList(obj["pros"] ?: obj["advantages"] ?: obj["strengths"])
        val cons = parseStringList(obj["cons"] ?: obj["disadvantages"] ?: obj["weaknesses"])

        val rawItems = obj["items"] ?: obj["bullets"] ?: obj["steps"]
        val stringItems = parseStringList(rawItems)
        val keyValues = parseKeyValues(
            obj["key_values"]
                ?: obj["keyValues"]
                ?: obj["pairs"]
                ?: obj["definitions"]
                ?: obj["metrics"]
                ?: (if (kind == IntelligentUiComponentKind.KEY_VALUE ||
                    kind == IntelligentUiComponentKind.DEFINITION_LIST ||
                    kind == IntelligentUiComponentKind.COMPARISON_ROW
                ) rawItems else null),
            singleKey = obj.stringOrNull("key") ?: obj.stringOrNull("left"),
            singleValue = obj.stringOrNull("value") ?: obj.stringOrNull("right"),
            singleHint = obj.stringOrNull("hint") ?: obj.stringOrNull("delta"),
            singleValueFieldId = valueFieldId,
            singleUnit = obj.stringOrNull("unit"),
        )

        val tableHeaders = parseStringList(obj["headers"] ?: obj["columns"])
        val tableRows = parseTableRows(obj["rows"] ?: (if (kind == IntelligentUiComponentKind.TABLE) obj["data"] else null), tableHeaders)

        val points = parseDataPoints(
            obj["points"]
                ?: obj["series"]
                ?: obj["bars"]
                ?: (if (kind == IntelligentUiComponentKind.BAR_CHART ||
                    kind == IntelligentUiComponentKind.LINE_CHART ||
                    kind == IntelligentUiComponentKind.RANKING_LIST
                ) obj["data"] ?: rawItems else null),
            nextId = nextId,
        )

        val tabs = parseTabsArray(obj["tabs"] as? JsonArray, nextId)

        val children = ArrayList<IntelligentUiComponentNode>()
        listOf("children", "components", "sections", "fields", "left", "primary").forEach { childKey ->
            (obj[childKey] as? JsonArray)?.forEach { childEl ->
                parseComponentNode(childEl, nextId)?.let(children::add)
            }
            (obj[childKey] as? JsonObject)?.let { singleObj ->
                parseComponentNode(singleObj, nextId)?.let(children::add)
            }
        }
        // If `items` or `cards` contains nested component objects (e.g., inside `section`, `grid`, `row`, `column`, `metricGrid`, `accordion`)
        val objectItemsSource = (rawItems as? JsonArray) ?: (obj["cards"] as? JsonArray)
        if (children.isEmpty() &&
            objectItemsSource != null &&
            kind !in setOf(
                IntelligentUiComponentKind.KEY_VALUE,
                IntelligentUiComponentKind.DEFINITION_LIST,
                IntelligentUiComponentKind.COMPARISON_ROW,
                IntelligentUiComponentKind.BAR_CHART,
                IntelligentUiComponentKind.LINE_CHART,
            )
        ) {
            val defaultChildKind = when (kind) {
                IntelligentUiComponentKind.METRIC_GRID -> IntelligentUiComponentKind.METRIC
                IntelligentUiComponentKind.ACCORDION -> IntelligentUiComponentKind.DISCLOSURE
                IntelligentUiComponentKind.GALLERY_STRIP -> IntelligentUiComponentKind.IMAGE_CARD
                IntelligentUiComponentKind.ACTION_BAR -> IntelligentUiComponentKind.ACTION_BUTTON
                else -> null
            }
            objectItemsSource.forEach { itemEl ->
                if (itemEl is JsonObject) {
                    parseComponentNode(itemEl, nextId, defaultKind = defaultChildKind)?.let(children::add)
                }
            }
        }

        val secondaryChildren = ArrayList<IntelligentUiComponentNode>()
        listOf("right", "secondary").forEach { secKey ->
            (obj[secKey] as? JsonArray)?.forEach { childEl ->
                parseComponentNode(childEl, nextId)?.let(secondaryChildren::add)
            }
            (obj[secKey] as? JsonObject)?.let { singleObj ->
                parseComponentNode(singleObj, nextId)?.let(secondaryChildren::add)
            }
        }

        val correctOption = obj.stringOrNull("answer")
            ?: obj.stringOrNull("correct_option")
            ?: obj.stringOrNull("correctOption")
            ?: obj.stringOrNull("correct_answer")
            ?: obj.stringOrNull("correctAnswer")
        val correctIndex = obj.intOrNull("correct_index")
            ?: obj.intOrNull("correctIndex")
            ?: obj.intOrNull("answer_index")
            ?: obj.intOrNull("answerIndex")

        return IntelligentUiComponentNode(
            id = id,
            kind = kind,
            title = title?.trim()?.takeIf { it.isNotBlank() },
            subtitle = subtitle?.trim()?.takeIf { it.isNotBlank() },
            text = text?.trim()?.takeIf { it.isNotBlank() },
            badge = (obj.stringOrNull("badge") ?: obj.stringOrNull("tag") ?: obj.stringOrNull("status") ?: obj.stringOrNull("pill"))
                ?.trim()?.takeIf { it.isNotBlank() },
            icon = obj.stringOrNull("icon")?.trim()?.takeIf { it.isNotBlank() },
            url = (obj.stringOrNull("url") ?: obj.stringOrNull("href") ?: obj.stringOrNull("image_url") ?: obj.stringOrNull("imageUrl"))
                ?.trim()?.takeIf { it.isNotBlank() },
            code = (obj.stringOrNull("code") ?: obj.stringOrNull("snippet"))?.takeIf { it.isNotBlank() },
            language = (obj.stringOrNull("language") ?: obj.stringOrNull("lang"))?.trim()?.takeIf { it.isNotBlank() },
            placeholder = obj.stringOrNull("placeholder")?.trim()?.takeIf { it.isNotBlank() },
            hint = (obj.stringOrNull("hint") ?: obj.stringOrNull("helper") ?: obj.stringOrNull("delta") ?: obj.stringOrNull("trend"))
                ?.trim()?.takeIf { it.isNotBlank() },
            explanation = (obj.stringOrNull("explanation") ?: obj.stringOrNull("rationale") ?: obj.stringOrNull("analysis"))
                ?.trim()?.takeIf { it.isNotBlank() },
            unit = obj.stringOrNull("unit")?.trim(),
            prefix = obj.stringOrNull("prefix")?.trim(),
            suffix = obj.stringOrNull("suffix")?.trim(),
            required = obj.booleanOrNull("required") ?: false,
            defaultValue = defaultVal,
            numericValue = numericVal,
            min = obj.doubleOrNull("min") ?: obj.doubleOrNull("minimum"),
            max = obj.doubleOrNull("max") ?: obj.doubleOrNull("maximum") ?: obj.doubleOrNull("total"),
            step = obj.doubleOrNull("step"),
            scale = obj.doubleOrNull("scale") ?: obj.doubleOrNull("multiplier") ?: 1.0,
            offset = obj.doubleOrNull("offset") ?: 0.0,
            valueFieldId = valueFieldId?.trim()?.takeIf { it.isNotBlank() },
            recommended = obj.booleanOrNull("recommended") ?: false,
            checked = obj.booleanOrNull("checked") ?: obj.booleanOrNull("selected") ?: obj.booleanOrNull("enabled"),
            expandedByDefault = obj.booleanOrNull("expanded") ?: obj.booleanOrNull("open") ?: false,
            columnsCount = (obj.intOrNull("columns") ?: obj.intOrNull("cols") ?: 2).coerceIn(1, 4),
            options = options,
            pros = pros,
            cons = cons,
            items = stringItems,
            keyValues = keyValues,
            tableHeaders = tableHeaders,
            tableRows = tableRows,
            points = points,
            tabs = tabs,
            children = children,
            secondaryChildren = secondaryChildren,
            correctOption = correctOption?.trim()?.takeIf { it.isNotBlank() },
            correctIndex = correctIndex,
            difficulty = obj.stringOrNull("difficulty")?.trim()?.takeIf { it.isNotBlank() },
            actionInstruction = (obj.stringOrNull("instruction") ?: obj.stringOrNull("prompt") ?: obj.stringOrNull("action"))
                ?.trim()?.takeIf { it.isNotBlank() },
            style = style,
        )
    }

    private fun inferNodeKindFromShape(obj: JsonObject): IntelligentUiComponentKind? = when {
        obj.containsKey("front") && obj.containsKey("back") -> IntelligentUiComponentKind.FLASHCARD
        obj.containsKey("question") && (obj.containsKey("options") || obj.containsKey("choices")) &&
            (obj.containsKey("answer") || obj.containsKey("correct_index") || obj.containsKey("correctIndex") || obj.containsKey("explanation")) ->
            IntelligentUiComponentKind.QUIZ_ITEM
        obj.containsKey("pros") || obj.containsKey("cons") ->
            if (obj.containsKey("title") || obj.containsKey("label")) IntelligentUiComponentKind.OPTION_CARD else IntelligentUiComponentKind.PROS_CONS
        obj.containsKey("tabs") -> IntelligentUiComponentKind.TABS
        obj.containsKey("headers") || obj.containsKey("rows") -> IntelligentUiComponentKind.TABLE
        obj.containsKey("min") && obj.containsKey("max") -> IntelligentUiComponentKind.SLIDER
        obj.containsKey("options") || obj.containsKey("choices") -> IntelligentUiComponentKind.RADIO_GROUP
        obj.containsKey("code") -> IntelligentUiComponentKind.CODE_BLOCK
        obj.containsKey("children") || obj.containsKey("components") -> IntelligentUiComponentKind.SECTION
        obj.containsKey("title") || obj.containsKey("text") || obj.containsKey("body") -> IntelligentUiComponentKind.CARD
        else -> null
    }

    private fun parseFlashcardElement(
        element: JsonElement,
        nextId: (String) -> String,
    ): IntelligentUiComponentNode? {
        val obj = element as? JsonObject ?: return null
        val front = obj.stringOrNull("front")
            ?: obj.stringOrNull("question")
            ?: obj.stringOrNull("term")
            ?: obj.stringOrNull("title")
            ?: return null
        val back = obj.stringOrNull("back")
            ?: obj.stringOrNull("answer")
            ?: obj.stringOrNull("definition")
            ?: obj.stringOrNull("text")
            ?: return null
        return IntelligentUiComponentNode(
            id = obj.stringOrNull("id") ?: nextId("flashcard"),
            kind = IntelligentUiComponentKind.FLASHCARD,
            title = front.trim(),
            text = back.trim(),
            badge = (obj.stringOrNull("tag") ?: obj.stringOrNull("badge") ?: obj.stringOrNull("category"))?.trim(),
            hint = obj.stringOrNull("hint")?.trim(),
            explanation = obj.stringOrNull("example") ?: obj.stringOrNull("explanation"),
            style = parseStyle(obj["style"] as? JsonObject ?: obj),
        )
    }

    private fun parseQuizElement(
        element: JsonElement,
        nextId: (String) -> String,
    ): IntelligentUiComponentNode? {
        val obj = element as? JsonObject ?: return null
        val question = obj.stringOrNull("question")
            ?: obj.stringOrNull("title")
            ?: obj.stringOrNull("prompt")
            ?: return null
        val options = parseOptionList(obj["options"] ?: obj["choices"])
        if (options.isEmpty()) return null
        return IntelligentUiComponentNode(
            id = obj.stringOrNull("id") ?: nextId("quiz"),
            kind = IntelligentUiComponentKind.QUIZ_ITEM,
            title = question.trim(),
            subtitle = obj.stringOrNull("subtitle")?.trim(),
            badge = (obj.stringOrNull("badge") ?: obj.stringOrNull("tag") ?: obj.stringOrNull("difficulty"))?.trim(),
            hint = obj.stringOrNull("hint")?.trim(),
            explanation = (obj.stringOrNull("explanation") ?: obj.stringOrNull("rationale") ?: obj.stringOrNull("analysis"))?.trim(),
            options = options,
            correctOption = (
                obj.stringOrNull("answer")
                    ?: obj.stringOrNull("correct_option")
                    ?: obj.stringOrNull("correctOption")
                    ?: obj.stringOrNull("correct_answer")
                    ?: obj.stringOrNull("correctAnswer")
                )?.trim(),
            correctIndex = obj.intOrNull("correct_index")
                ?: obj.intOrNull("correctIndex")
                ?: obj.intOrNull("answer_index")
                ?: obj.intOrNull("answerIndex"),
            difficulty = obj.stringOrNull("difficulty")?.trim(),
            style = parseStyle(obj["style"] as? JsonObject ?: obj),
        )
    }

    private fun parseOptionList(element: JsonElement?): List<IntelligentUiOptionItem> {
        val array = element as? JsonArray ?: return emptyList()
        return array.mapIndexedNotNull { index, item ->
            when (item) {
                is JsonPrimitive -> {
                    val label = item.contentOrNull?.trim()?.takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
                    IntelligentUiOptionItem(id = "opt_$index", label = label, value = label)
                }
                is JsonObject -> {
                    val label = item.stringOrNull("label")
                        ?: item.stringOrNull("title")
                        ?: item.stringOrNull("name")
                        ?: item.stringOrNull("text")
                        ?: item.stringOrNull("value")
                        ?: return@mapIndexedNotNull null
                    val id = item.stringOrNull("id")
                        ?: item.stringOrNull("key")
                        ?: item.stringOrNull("value")
                        ?: "opt_${index + 1}_${label.hashCode()}"
                    val value = item.stringOrNull("value") ?: id
                    IntelligentUiOptionItem(
                        id = id,
                        label = label.trim(),
                        value = value.trim(),
                        subtitle = (item.stringOrNull("subtitle") ?: item.stringOrNull("summary") ?: item.stringOrNull("price"))?.trim(),
                        description = (item.stringOrNull("description") ?: item.stringOrNull("body") ?: item.stringOrNull("rationale"))?.trim(),
                        badge = (item.stringOrNull("badge") ?: item.stringOrNull("tag") ?: item.stringOrNull("score"))?.trim(),
                        pros = parseStringList(item["pros"] ?: item["advantages"]),
                        cons = parseStringList(item["cons"] ?: item["disadvantages"]),
                        metrics = parseKeyValues(item["metrics"] ?: item["specs"]),
                        recommended = item.booleanOrNull("recommended") ?: false,
                        tone = IntelligentUiTone.fromRaw(item.stringOrNull("tone")),
                    )
                }
                else -> null
            }
        }
    }

    private fun parseTabsArray(
        array: JsonArray?,
        nextId: (String) -> String,
    ): List<IntelligentUiTabItem> {
        if (array == null) return emptyList()
        return array.mapIndexedNotNull { index, item ->
            val obj = item as? JsonObject ?: return@mapIndexedNotNull null
            val title = obj.stringOrNull("title")
                ?: obj.stringOrNull("label")
                ?: obj.stringOrNull("name")
                ?: "Tab ${index + 1}"
            val id = obj.stringOrNull("id") ?: obj.stringOrNull("key") ?: "tab_${index + 1}"
            val children = ArrayList<IntelligentUiComponentNode>()
            listOf("children", "components", "sections", "items", "body").forEach { key ->
                (obj[key] as? JsonArray)?.forEach { childEl ->
                    parseComponentNode(childEl, nextId)?.let(children::add)
                }
            }
            if (children.isEmpty()) {
                obj.stringOrNull("content")?.takeIf { it.isNotBlank() }?.let { content ->
                    children += IntelligentUiComponentNode(
                        id = nextId("tab_text"),
                        kind = IntelligentUiComponentKind.TEXT,
                        text = content,
                    )
                }
            }
            IntelligentUiTabItem(
                id = id,
                title = title.trim(),
                badge = obj.stringOrNull("badge")?.trim(),
                children = children,
            )
        }
    }

    private fun parseStringList(element: JsonElement?): List<String> {
        val array = element as? JsonArray ?: return emptyList()
        return array.mapNotNull { item ->
            when (item) {
                is JsonPrimitive -> item.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
                is JsonObject -> (
                    item.stringOrNull("text")
                        ?: item.stringOrNull("label")
                        ?: item.stringOrNull("title")
                    )?.trim()?.takeIf { it.isNotBlank() }
                else -> null
            }
        }
    }

    private fun parseKeyValues(
        element: JsonElement?,
        singleKey: String? = null,
        singleValue: String? = null,
        singleHint: String? = null,
        singleValueFieldId: String? = null,
        singleUnit: String? = null,
    ): List<IntelligentUiKeyValue> {
        val list = ArrayList<IntelligentUiKeyValue>()
        when (element) {
            is JsonArray -> element.forEach { item ->
                val obj = item as? JsonObject ?: return@forEach
                val k = obj.stringOrNull("key")
                    ?: obj.stringOrNull("label")
                    ?: obj.stringOrNull("term")
                    ?: obj.stringOrNull("name")
                    ?: obj.stringOrNull("left")
                    ?: return@forEach
                val v = obj.stringOrNull("value")
                    ?: obj.stringOrNull("definition")
                    ?: obj.stringOrNull("right")
                    ?: obj.stringOrNull("text")
                    ?: ""
                list += IntelligentUiKeyValue(
                    key = k.trim(),
                    value = v.trim(),
                    hint = (obj.stringOrNull("hint") ?: obj.stringOrNull("delta") ?: obj.stringOrNull("note"))?.trim(),
                    valueFieldId = obj.stringOrNull("value_field_id") ?: obj.stringOrNull("valueFieldId"),
                    unit = obj.stringOrNull("unit")?.trim(),
                    tone = IntelligentUiTone.fromRaw(obj.stringOrNull("tone")),
                )
            }
            is JsonObject -> element.entries.forEach { (k, v) ->
                val textVal = (v as? JsonPrimitive)?.contentOrNull ?: v.toString()
                list += IntelligentUiKeyValue(key = k, value = textVal)
            }
            else -> Unit
        }
        if (list.isEmpty() && !singleKey.isNullOrBlank() && (!singleValue.isNullOrBlank() || !singleValueFieldId.isNullOrBlank())) {
            list += IntelligentUiKeyValue(
                key = singleKey.trim(),
                value = singleValue?.trim().orEmpty(),
                hint = singleHint?.trim(),
                valueFieldId = singleValueFieldId,
                unit = singleUnit,
            )
        }
        return list
    }

    private fun parseTableRows(
        element: JsonElement?,
        headers: List<String>,
    ): List<List<String>> {
        val array = element as? JsonArray ?: return emptyList()
        return array.mapNotNull { rowEl ->
            when (rowEl) {
                is JsonArray -> rowEl.map { cell ->
                    (cell as? JsonPrimitive)?.contentOrNull?.trim() ?: cell.toString()
                }
                is JsonObject -> {
                    if (headers.isNotEmpty()) {
                        headers.map { header ->
                            rowEl.stringOrNull(header)
                                ?: rowEl.entries.firstOrNull { it.key.equals(header, ignoreCase = true) }
                                    ?.value?.let { (it as? JsonPrimitive)?.contentOrNull }
                                ?: ""
                        }
                    } else {
                        rowEl.values.map { cell ->
                            (cell as? JsonPrimitive)?.contentOrNull?.trim() ?: cell.toString()
                        }
                    }
                }
                else -> null
            }
        }
    }

    private fun parseDataPoints(
        element: JsonElement?,
        nextId: (String) -> String,
    ): List<IntelligentUiDataPoint> {
        val array = element as? JsonArray ?: return emptyList()
        return array.mapIndexedNotNull { index, item ->
            when (item) {
                is JsonPrimitive -> {
                    val v = item.doubleOrNull ?: return@mapIndexedNotNull null
                    IntelligentUiDataPoint(
                        id = nextId("pt"),
                        label = "#${index + 1}",
                        value = v,
                    )
                }
                is JsonObject -> {
                    val label = item.stringOrNull("label")
                        ?: item.stringOrNull("name")
                        ?: item.stringOrNull("x")
                        ?: item.stringOrNull("title")
                        ?: "#${index + 1}"
                    val value = item.doubleOrNull("value")
                        ?: item.doubleOrNull("y")
                        ?: item.doubleOrNull("score")
                        ?: item.doubleOrNull("amount")
                        ?: 0.0
                    IntelligentUiDataPoint(
                        id = item.stringOrNull("id") ?: nextId("pt"),
                        label = label.trim(),
                        value = value,
                        max = item.doubleOrNull("max") ?: item.doubleOrNull("total"),
                        unit = item.stringOrNull("unit")?.trim(),
                        delta = (item.stringOrNull("delta") ?: item.stringOrNull("badge") ?: item.stringOrNull("subtitle"))?.trim(),
                        valueFieldId = (item.stringOrNull("value_field_id") ?: item.stringOrNull("valueFieldId"))?.trim(),
                        scale = item.doubleOrNull("scale") ?: item.doubleOrNull("multiplier") ?: 1.0,
                        offset = item.doubleOrNull("offset") ?: 0.0,
                        tone = IntelligentUiTone.fromRaw(item.stringOrNull("tone")),
                    )
                }
                else -> null
            }
        }
    }

    private fun parseStyle(obj: JsonObject?): IntelligentUiStyle {
        if (obj == null) return IntelligentUiStyle()
        return IntelligentUiStyle(
            tone = IntelligentUiTone.fromRaw(obj.stringOrNull("tone") ?: obj.stringOrNull("variant") ?: obj.stringOrNull("severity")),
            emphasis = IntelligentUiEmphasis.fromRaw(obj.stringOrNull("emphasis") ?: obj.stringOrNull("prominence")),
            density = IntelligentUiDensity.fromRaw(obj.stringOrNull("density") ?: obj.stringOrNull("size")),
            alignment = IntelligentUiAlignment.fromRaw(obj.stringOrNull("alignment") ?: obj.stringOrNull("align")),
            spacing = IntelligentUiSizeToken.fromRaw(obj.stringOrNull("spacing") ?: obj.stringOrNull("gap")),
            padding = IntelligentUiSizeToken.fromRaw(obj.stringOrNull("padding")),
        )
    }

    private fun parseActions(
        element: JsonElement?,
        copy: GenerativeWidgetCopy,
    ): List<GenerativeWidgetAction> {
        val array = element as? JsonArray ?: return emptyList()
        return array.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val label = obj.stringOrNull("label")?.trim()?.takeIf { it.length in 1..24 } ?: return@mapNotNull null
            val instruction = (obj.stringOrNull("instruction") ?: obj.stringOrNull("prompt") ?: label)
                .trim()
                .takeIf { it.length in 1..240 }
                ?: return@mapNotNull null
            val id = (obj.stringOrNull("id") ?: label).trim().take(48)
            GenerativeWidgetAction(
                id = id,
                label = label,
                instruction = instruction,
                promptCopy = copy,
            )
        }.take(3)
    }

    private fun extractJsonString(source: String, key: String): String? {
        val regex = Regex(""""${Regex.escape(key)}"\s*:\s*"((?:\\.|[^"\\])*)"?""")
        val match = regex.find(source) ?: return null
        return match.groupValues[1]
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\\\", "\\")
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.doubleOrNull(key: String): Double? =
        (this[key] as? JsonPrimitive)?.let { prim ->
            prim.doubleOrNull ?: prim.contentOrNull?.trim()?.toDoubleOrNull()
        }

    private fun JsonObject.intOrNull(key: String): Int? =
        (this[key] as? JsonPrimitive)?.let { prim ->
            prim.intOrNull ?: prim.contentOrNull?.trim()?.toIntOrNull()
        }

    private fun JsonObject.booleanOrNull(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.let { prim ->
            prim.booleanOrNull ?: prim.contentOrNull?.trim()?.lowercase()?.let {
                when (it) {
                    "true", "1", "yes" -> true
                    "false", "0", "no" -> false
                    else -> null
                }
            }
        }
}
