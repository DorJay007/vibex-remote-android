package dev.vibex.companion

import dev.vibex.remote.v1.JsonValue

data class QuestionChoice(
    val value: String,
    val label: String,
    val description: String? = null,
    val recommended: Boolean = false,
) {
    val id: String get() = value
}

data class FormQuestion(
    val id: String,
    val header: String? = null,
    val title: String,
    val description: String? = null,
    val options: List<QuestionChoice> = emptyList(),
    val multiSelect: Boolean = false,
    val allowCustom: Boolean = false,
    val customPlaceholder: String? = null,
    val required: Boolean = true,
)

data class QuestionAnswer(
    val selected: List<String> = emptyList(),
    val customText: String = "",
    val useCustom: Boolean = false,
)

object QuestionForm {
    private const val RECOMMENDED_PREFIX = "(Recommended)"

    fun questions(request: PendingQuestion): List<FormQuestion> {
        val parsed = parseSchema(request)
        if (parsed.isNotEmpty()) return parsed

        val options = request.options.map { option ->
            val (label, recommended) = stripRecommendedPrefix(option.label)
            QuestionChoice(
                value = option.id,
                label = label,
                recommended = recommended,
            )
        }
        return listOf(
            FormQuestion(
                id = "answer",
                header = null,
                title = request.prompt.ifBlank { "请确认以下问题" },
                description = null,
                options = options,
                multiSelect = false,
                allowCustom = true,
                customPlaceholder = null,
                required = true,
            ),
        )
    }

    fun content(
        request: PendingQuestion,
        questions: List<FormQuestion>,
        answers: Map<String, QuestionAnswer>,
    ): JsonValue {
        val schemaObj = request.schema?.asObject()?.value
        val hasSchemaProps = (schemaObj?.get("properties")?.asObject()?.value?.isNotEmpty() == true) ||
            (schemaObj?.get("x-vibex-questions")?.asArray()?.value?.isNotEmpty() == true)

        if (!hasSchemaProps && questions.size == 1 && questions.first().id == "answer") {
            val answer = answers["answer"] ?: QuestionAnswer()
            val trimmed = answer.customText.trim()
            val text = if ((answer.useCustom || questions.first().options.isEmpty()) && trimmed.isNotEmpty()) {
                trimmed
            } else {
                answer.selected.firstOrNull().orEmpty()
            }
            return JsonValue.Object(mapOf("text" to JsonValue.Text(text)))
        }

        val payload = linkedMapOf<String, JsonValue>()
        for (question in questions) {
            val answer = answers[question.id] ?: QuestionAnswer()
            val trimmed = answer.customText.trim()
            if (question.multiSelect) {
                val values = answer.selected.toMutableList()
                if (answer.useCustom && trimmed.isNotEmpty()) {
                    values.add(trimmed)
                }
                payload[question.id] = JsonValue.Array(values.map { JsonValue.Text(it) })
            } else if ((answer.useCustom || question.options.isEmpty()) && trimmed.isNotEmpty()) {
                payload[question.id] = JsonValue.Text(trimmed)
            } else if (answer.selected.isNotEmpty()) {
                val first = answer.selected.first()
                if (request.options.isNotEmpty() && schemaObj?.get("x-vibex-questions") == null) {
                    payload["option_id"] = JsonValue.Text(first)
                }
                payload[question.id] = JsonValue.Text(first)
            }
        }
        return JsonValue.Object(payload)
    }

    fun parseQuestions(schema: JsonValue): List<FormQuestion> {
        val root = schema.asObject()?.value ?: return emptyList()
        val vibexQuestions = root["x-vibex-questions"]?.asArray()?.value
        if (!vibexQuestions.isNullOrEmpty()) {
            val requiredSet = root["required"]?.asArray()?.value
                ?.mapNotNull { it.asText() }
                ?.toSet()
                .orEmpty()
            val items = vibexQuestions.mapIndexedNotNull { index, raw ->
                val item = raw.asObject()?.value ?: return@mapIndexedNotNull null
                val id = item["id"]?.asText()?.takeIf { it.isNotBlank() } ?: "q_$index"
                val title = item["question"]?.asText()
                    ?: item["title"]?.asText()
                    ?: id
                val rawOptions = item["options"]?.asArray()?.value.orEmpty()
                val choices = rawOptions.mapNotNull { optRaw ->
                    val opt = optRaw.asObject()?.value ?: return@mapNotNull null
                    val rawLabel = opt["label"]?.asText() ?: opt["title"]?.asText() ?: return@mapNotNull null
                    val (cleanLabel, prefixRecommended) = stripRecommendedPrefix(rawLabel)
                    val value = opt["value"]?.asText()?.takeIf { it.isNotBlank() } ?: cleanLabel
                    QuestionChoice(
                        value = value,
                        label = cleanLabel,
                        description = opt["description"]?.asText(),
                        recommended = (opt["recommended"]?.asBool() == true) || prefixRecommended,
                    )
                }
                FormQuestion(
                    id = id,
                    header = item["header"]?.asText(),
                    title = title,
                    description = item["description"]?.asText(),
                    options = choices,
                    multiSelect = item["multiSelect"]?.asBool() ?: (item["multi_select"]?.asBool() ?: false),
                    allowCustom = item["allowCustom"]?.asBool() ?: true,
                    customPlaceholder = item["customPlaceholder"]?.asText(),
                    required = requiredSet.isEmpty() || requiredSet.contains(id),
                )
            }
            if (items.isNotEmpty()) return items
        }

        val props = root["properties"]?.asObject()?.value
        if (!props.isNullOrEmpty()) {
            val requiredSet = root["required"]?.asArray()?.value
                ?.mapNotNull { it.asText() }
                ?.toSet()
                .orEmpty()
            val items = props.keys.sorted().mapNotNull { key ->
                if (key.endsWith("_custom")) return@mapNotNull null
                val prop = props[key]?.asObject()?.value ?: return@mapNotNull null
                val propType = prop["type"]?.asText() ?: "string"
                val isArray = propType == "array"
                val itemsSpec = prop["items"]?.asObject()?.value
                val choices = extractChoices(if (isArray) (itemsSpec ?: prop) else prop)
                val customSpec = props["${key}_custom"]?.asObject()?.value
                FormQuestion(
                    id = key,
                    header = prop["x-header"]?.asText(),
                    title = prop["title"]?.asText() ?: prop["description"]?.asText() ?: key,
                    description = if (prop["title"]?.asText() != null) prop["description"]?.asText() else null,
                    options = choices,
                    multiSelect = isArray,
                    allowCustom = customSpec != null || choices.isEmpty(),
                    customPlaceholder = customSpec?.get("description")?.asText() ?: customSpec?.get("title")?.asText(),
                    required = requiredSet.contains(key),
                )
            }
            if (items.isNotEmpty()) return items
        }
        return emptyList()
    }

    private fun parseSchema(request: PendingQuestion): List<FormQuestion> {
        val schema = request.schema ?: return emptyList()
        return parseQuestions(schema)
    }

    private fun extractChoices(spec: Map<String, JsonValue>): List<QuestionChoice> {
        val list = spec["oneOf"]?.asArray()?.value ?: spec["anyOf"]?.asArray()?.value
        if (!list.isNullOrEmpty()) {
            return list.mapNotNull { entry ->
                val obj = entry.asObject()?.value ?: return@mapNotNull null
                val constVal = obj["const"]?.asText() ?: obj["enum"]?.asArray()?.value?.firstOrNull()?.asText() ?: return@mapNotNull null
                val rawLabel = obj["title"]?.asText() ?: constVal
                val (cleanLabel, recommended) = stripRecommendedPrefix(rawLabel)
                QuestionChoice(
                    value = constVal,
                    label = cleanLabel,
                    description = obj["description"]?.asText(),
                    recommended = recommended,
                )
            }
        }
        val enums = spec["enum"]?.asArray()?.value
        if (!enums.isNullOrEmpty()) {
            val names = (spec["enumNames"]?.asArray()?.value ?: spec["x-enum-names"]?.asArray()?.value)
                ?.mapNotNull { it.asText() }
                .orEmpty()
            return enums.mapIndexedNotNull { index, raw ->
                val valStr = raw.asText() ?: return@mapIndexedNotNull null
                val rawLabel = names.getOrNull(index) ?: valStr
                val (cleanLabel, recommended) = stripRecommendedPrefix(rawLabel)
                QuestionChoice(value = valStr, label = cleanLabel, recommended = recommended)
            }
        }
        return emptyList()
    }

    fun stripRecommendedPrefix(label: String): Pair<String, Boolean> {
        val trimmed = label.trim()
        if (trimmed.lowercase().startsWith(RECOMMENDED_PREFIX.lowercase())) {
            val stripped = trimmed.drop(RECOMMENDED_PREFIX.length).trim()
            return (stripped.ifEmpty { trimmed }) to true
        }
        return trimmed to false
    }
}
