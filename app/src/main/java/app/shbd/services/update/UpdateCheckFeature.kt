package app.shbd.services.update

/**
 * Product gate for the release check (Codeberg releases API inherited from Chompass).
 *
 * SHBD has no release feed yet, so the check is off: no network call on start or in About,
 * no "update available" notification. Flip once SHBD has its own distribution channel and
 * [AndroidUpdateChecker] points at it. Do not delete the implementation.
 */
object UpdateCheckFeature {
    const val ENABLED: Boolean = false
}
