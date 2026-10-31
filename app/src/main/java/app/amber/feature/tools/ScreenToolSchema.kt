package app.amber.feature.tools

import app.amber.core.automation.ScreenOcrMode
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal fun screenStringProp(description: String) = buildJsonObject {
    put("type", "string")
    put("description", description)
}

internal fun screenNumberProp(description: String) = buildJsonObject {
    put("type", "number")
    put("description", description)
}

internal fun screenIntegerProp(description: String) = buildJsonObject {
    put("type", "integer")
    put("description", description)
}

internal fun JsonElement.booleanOrNull(name: String): Boolean? =
    jsonObject[name]?.jsonPrimitive?.contentOrNull?.lowercase()?.let {
        when (it) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }

internal fun JsonElement.resolveOcrMode(): ScreenOcrMode {
    booleanOrNull("include_ocr")?.let { include ->
        return if (include) ScreenOcrMode.ON else ScreenOcrMode.OFF
    }
    return ScreenOcrMode.fromInput(string("ocr_mode"))
}
