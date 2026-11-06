package app.manzil.offline.location

/** Pure ownership state: an Activity release must not cancel foreground-service GPS. */
internal class GpsOwnership {
    var previewOwners: Int = 0
        private set
    var serviceOwns: Boolean = false
        private set
    val shouldRun: Boolean get() = previewOwners > 0 || serviceOwns
    fun acquirePreview() { previewOwners++ }
    fun releasePreview() { if (previewOwners > 0) previewOwners-- }
    fun setService(active: Boolean) { serviceOwns = active }
}

