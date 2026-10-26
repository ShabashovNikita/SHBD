package app.shbd.ui.diary.blocks

/** The six Diary dashboard cards, each with its own block screen (`diary/block/{id}`). */
enum class DiaryBlock(val id: String) {
    NUTRITION("nutrition"),
    ACTIVITY("activity"),
    STEPS("steps"),
    WATER("water"),
    SLEEP("sleep"),
    WEIGHT("weight");

    companion object {
        /** Null for an unknown id — the caller falls back to the dashboard. */
        fun fromId(id: String?): DiaryBlock? = entries.firstOrNull { it.id == id }
    }
}
