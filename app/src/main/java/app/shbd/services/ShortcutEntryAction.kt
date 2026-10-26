package app.shbd.services

/**
 * Launcher long-press shortcuts that deep-link into Home food-entry flows.
 * Stable action strings are intentional — OEM launchers and pinned shortcuts
 * may keep them across app updates.
 */
enum class ShortcutEntryAction(val action: String) {
    CAMERA("app.shbd.action.SHORTCUT_CAMERA"),
    VOICE("app.shbd.action.SHORTCUT_VOICE"),
    BARCODE("app.shbd.action.SHORTCUT_BARCODE"),
    /** #182: launcher long-press toggle for the fasting timer (no destination UI). */
    FASTING("app.shbd.action.SHORTCUT_FASTING");

    companion object {
        fun fromAction(action: String?): ShortcutEntryAction? =
            entries.firstOrNull { it.action == action }

        fun fromIntentExtra(extra: String?): ShortcutEntryAction? = when (extra?.lowercase()) {
            "camera", "photo", "camera_note" -> CAMERA
            "voice", "mic" -> VOICE
            "barcode", "scan" -> BARCODE
            "fasting", "fast" -> FASTING
            else -> null
        }
    }
}
