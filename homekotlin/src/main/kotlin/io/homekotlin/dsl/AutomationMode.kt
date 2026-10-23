package io.homekotlin.dsl

/**
 * Execution mode for automations, identical to Home Assistant's "körläge".
 *
 * Determines what happens when an automation is triggered while an instance is already running:
 * - [SINGLE] ("Enskild"): Do not start a new run. Issue a warning.
 * - [RESTART] ("Starta om"): Cancel the running instance and immediately start a new run.
 * - [QUEUED] ("På kö"): Wait until all previous runs complete, executing sequentially in FIFO order (up to [maxRuns]).
 * - [PARALLEL] ("Parallell"): Run independent executions concurrently in parallel (up to [maxRuns]).
 */
enum class AutomationMode(
    val id: String,
    val title: String,
    val description: String
) {
    SINGLE(
        id = "single",
        title = "Enskild",
        description = "Starta inte en ny körning. Utfärda en varning."
    ),
    RESTART(
        id = "restart",
        title = "Starta om",
        description = "Starta en ny körning efter att först ha stoppat den föregående körningen."
    ),
    QUEUED(
        id = "queued",
        title = "På kö",
        description = "Starta en ny körning efter att alla tidigare körningar har slutförts."
    ),
    PARALLEL(
        id = "parallel",
        title = "Parallell",
        description = "Starta en ny, oberoende körning parallellt med tidigare körningar."
    );

    companion object {
        val ENSKILD = SINGLE
        val STARTA_OM = RESTART
        val PA_KO = QUEUED
        val PARALLELL = PARALLEL

        fun fromString(value: String): AutomationMode = when (value.trim().lowercase()) {
            "restart", "starta_om", "starta om", "startaom" -> RESTART
            "queued", "queue", "på_kö", "pa_ko", "på kö", "pako" -> QUEUED
            "parallel", "parallell" -> PARALLEL
            "single", "enskild" -> SINGLE
            else -> SINGLE
        }
    }
}

/**
 * Swedish alias for [AutomationMode].
 */
typealias Körläge = AutomationMode
