package com.openaicodex.app.data

/**
 * Static catalog of selectable Codex models plus an effort level, shown in
 * the in-chat model switcher (the chip next to the "+" attach button).
 *
 * `relativeCost` is a coarse, static multiplier used only to render a
 * "yaklaşık token kullanımı" hint in the picker UI (e.g. "Yüksek çaba"
 * models cost more tokens per turn than "Düşük"). It is NOT a live token
 * count from any API — Codex CLI's JSON-lines protocol (see ThreadEvent)
 * does not currently emit a per-turn token total we could bind to real
 * usage, so this stays a static hint rather than a false precise number.
 */
enum class Effort(val label: String, val relativeCost: Int) {
    LOW("Low", 1),
    MEDIUM("Medium", 2),
    HIGH("High", 4),
    EXTRA("Extra", 7),
    MAX("Max", 12)
}

data class ModelOption(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val defaultEffort: Effort = Effort.MEDIUM,
    val requiresPro: Boolean = false
)

data class SelectedModel(
    val modelId: String,
    val effort: Effort
) {
    fun label(): String {
        val name = ModelCatalog.byId(modelId)?.displayName ?: modelId
        return "$name ${effort.label}"
    }
}

object ModelCatalog {
    val models: List<ModelOption> = listOf(
        ModelOption("fable-5-1", "Fable 5.1", "En zorlu görevler için", Effort.HIGH, requiresPro = true),
        ModelOption("opus-5", "Opus 5", "Karmaşık görevler için", Effort.MEDIUM, requiresPro = true),
        ModelOption("sonnet-5", "Sonnet 5", "Günlük görevler için en verimli", Effort.LOW),
        ModelOption("haiku-4-5", "Haiku 4.5", "Hızlı yanıtlar için en hızlısı", Effort.LOW)
    )

    fun byId(id: String): ModelOption? = models.find { it.id == id }

    val default = SelectedModel(modelId = "sonnet-5", effort = Effort.LOW)

    /** Coarse relative token-cost estimate for a model+effort pick, for display only. */
    fun estimatedRelativeCost(selection: SelectedModel): Int = selection.effort.relativeCost
}
