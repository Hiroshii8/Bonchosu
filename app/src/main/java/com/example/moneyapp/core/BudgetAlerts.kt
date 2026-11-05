package com.hiroshi.moneyapp.core

/** One notification to show: the highest alert level a budget has newly reached in its current period. */
data class BudgetAlert(val budgetId: Long, val level: Int, val progress: BudgetProgress)

/** Decides which budget alerts to send. Pure, so it's unit-tested; the caller posts them and stores [sent]. */
object BudgetAlerts {

    data class Input(val budgetId: Long, val levels: List<Int>, val progress: BudgetProgress)
    data class Result(val alerts: List<BudgetAlert>, val sent: Set<String>)

    /**
     * Each level fires once per budget period. When one expense jumps past several levels, only the highest is
     * shown, and the lower ones are marked as sent too. Keys from past periods or deleted budgets are dropped.
     */
    fun check(budgets: List<Input>, alreadySent: Set<String>): Result {
        val alerts = mutableListOf<BudgetAlert>()
        val sent = mutableSetOf<String>()
        for (b in budgets) {
            val percent = if (b.progress.limit <= 0) 0L else b.progress.spent * 100 / b.progress.limit
            val reached = b.levels.filter { it > 0 && percent >= it }
            val newlyReached = reached.filter { key(b, it) !in alreadySent }
            newlyReached.maxOrNull()?.let { alerts += BudgetAlert(b.budgetId, it, b.progress) }
            // Keep keys for levels still reached this period; spending can drop again (a deleted expense).
            reached.forEach { sent += key(b, it) }
        }
        return Result(alerts, sent)
    }

    private fun key(b: Input, level: Int) = "${b.budgetId}:${b.progress.range.startMs}:$level"

    /** "50,75,100" ⇄ [50, 75, 100]. */
    fun parseLevels(raw: String): List<Int> = raw.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..1000 }.distinct().sorted()
    fun formatLevels(levels: Collection<Int>): String = levels.distinct().sorted().joinToString(",")

    val DEFAULT_LEVELS = listOf(50, 75, 100)
    val LEVEL_CHOICES = listOf(50, 75, 90, 100, 120, 150)
}
