package app.manzil.model

/** How a saved step is travelled. Unknown values from storage decode to [OTHER]. */
enum class StepMode { WALK, TRAIN, OTHER }

data class JourneyStep(
    val id: String,
    val title: String,
    val detail: String,
    /** Grounded explanation shown for "explain"; written with the saved journey, never generated. */
    val explanation: String,
    val mode: StepMode,
    /** Optional honest caveat, e.g. "Service times are unverified." */
    val caution: String? = null,
)

data class Destination(
    val name: String,
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(latitude in -90.0..90.0) { "latitude out of range" }
        require(longitude in -180.0..180.0) { "longitude out of range" }
    }
}

data class Journey(
    val id: String,
    val originName: String,
    val destination: Destination,
    val steps: List<JourneyStep>,
    /** True for bundled demo content that is not verified operational transit information. */
    val isSample: Boolean,
    /** Human-readable provenance, e.g. "Bundled sample written for the Manzil demo". */
    val sourceLabel: String,
    /** When the content itself was written or prepared (not when it was saved to this phone). */
    val contentPreparedAtEpochMs: Long,
) {
    init {
        require(id.isNotBlank()) { "journey id is blank" }
        require(steps.isNotEmpty()) { "journey has no steps" }
        require(steps.map { it.id }.toSet().size == steps.size) { "duplicate step ids" }
    }

    /** Consecutive identical modes collapsed, for the compact route line. */
    val modeSequence: List<StepMode>
        get() = steps.map { it.mode }.fold(mutableListOf()) { acc, mode ->
            if (acc.lastOrNull() != mode) acc.add(mode)
            acc
        }
}

data class SavedJourney(
    val journey: Journey,
    val savedAtEpochMs: Long,
)

enum class ProgressStatus { ACTIVE, FINISHED }

/**
 * Durable progress through one saved journey. [stateVersion] increases on every accepted change
 * so tool results can say exactly which state they produced.
 */
data class JourneyProgress(
    val journeyId: String,
    val stepIndex: Int,
    val status: ProgressStatus,
    val startedAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val stateVersion: Long,
)

enum class LoadIssue {
    /** Stored data could not be parsed; it was backed up and the store reset. */
    CORRUPT_RESET,

    /** Stored data was written by a newer app version; this build will not overwrite it. */
    NEWER_SCHEMA,
}

data class StoredState(
    val journeys: List<SavedJourney>,
    val progress: JourneyProgress?,
    val loadIssue: LoadIssue? = null,
) {
    fun find(journeyId: String): SavedJourney? = journeys.firstOrNull { it.journey.id == journeyId }

    /** The journey that [progress] points at, if it still exists. */
    val activeJourney: SavedJourney?
        get() = progress?.let { find(it.journeyId) }

    val hasJourneyInProgress: Boolean
        get() = progress?.status == ProgressStatus.ACTIVE && activeJourney != null

    companion object {
        val EMPTY = StoredState(journeys = emptyList(), progress = null)
    }
}
