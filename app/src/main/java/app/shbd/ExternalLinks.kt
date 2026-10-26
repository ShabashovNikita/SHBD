package app.shbd

/**
 * Public web pages of SHBD (privacy policy, credits, support). Chompass pointed these at its
 * Codeberg repo; SHBD has no public pages yet, so they are null and the UI hides the rows
 * that would open them. Fill in once the pages exist (privacy policy: with the backend change;
 * credits/licenses: at the end of the project).
 */
object ExternalLinks {
    val PRIVACY_POLICY: String? = null
    val ASSET_CREDITS: String? = null
    val SUPPORT: String? = null

    /** Support and Legal hub rows are only shown when there is something to open. */
    val hasSupportPages: Boolean get() = SUPPORT != null
    val hasLegalPages: Boolean get() = PRIVACY_POLICY != null || ASSET_CREDITS != null
}
