package app.manzil.data

import app.manzil.model.Destination
import app.manzil.model.Journey
import app.manzil.model.JourneyProgress
import app.manzil.model.JourneyStep
import app.manzil.model.ProgressStatus
import app.manzil.model.SavedJourney
import app.manzil.model.StepMode
import app.manzil.model.StoredState
import org.json.JSONArray
import org.json.JSONObject

class NewerSchemaException(val found: Int) : Exception("Stored schema $found is newer than ${JourneyCodec.SCHEMA_VERSION}")

/**
 * Versioned JSON envelope for everything Manzil stores about journeys:
 * `{ schemaVersion, journeys: [{ savedAt, journey: {...} }], progress: {...} | null }`.
 */
object JourneyCodec {
    const val SCHEMA_VERSION = 1

    fun encode(state: StoredState): String {
        val root = JSONObject()
        root.put("schemaVersion", SCHEMA_VERSION)
        val journeys = JSONArray()
        state.journeys.forEach { journeys.put(encodeSaved(it)) }
        root.put("journeys", journeys)
        root.put("progress", state.progress?.let(::encodeProgress) ?: JSONObject.NULL)
        return root.toString()
    }

    /**
     * Throws [NewerSchemaException] for data from a newer build and any other exception for
     * malformed data. Progress pointing at a missing journey or an out-of-range step is dropped.
     */
    fun decode(raw: String): StoredState {
        val root = JSONObject(raw)
        val version = root.getInt("schemaVersion")
        if (version > SCHEMA_VERSION) throw NewerSchemaException(version)
        require(version >= 1) { "invalid schema version $version" }

        val array = root.getJSONArray("journeys")
        val journeys = (0 until array.length()).map { decodeSaved(array.getJSONObject(it)) }
        require(journeys.map { it.journey.id }.toSet().size == journeys.size) { "duplicate journey ids" }

        val progress = if (root.isNull("progress")) null else decodeProgress(root.getJSONObject("progress"))
        val validProgress = progress?.takeIf { p ->
            val journey = journeys.firstOrNull { it.journey.id == p.journeyId }?.journey
            journey != null && p.stepIndex in journey.steps.indices
        }
        return StoredState(journeys = journeys, progress = validProgress)
    }

    private fun encodeSaved(saved: SavedJourney): JSONObject = JSONObject()
        .put("savedAt", saved.savedAtEpochMs)
        .put("journey", encodeJourney(saved.journey))

    private fun decodeSaved(obj: JSONObject) = SavedJourney(
        journey = decodeJourney(obj.getJSONObject("journey")),
        savedAtEpochMs = obj.getLong("savedAt"),
    )

    private fun encodeJourney(journey: Journey): JSONObject {
        val steps = JSONArray()
        journey.steps.forEach { step ->
            val s = JSONObject()
                .put("id", step.id)
                .put("title", step.title)
                .put("detail", step.detail)
                .put("explanation", step.explanation)
                .put("mode", step.mode.name)
            if (step.caution != null) s.put("caution", step.caution)
            steps.put(s)
        }
        return JSONObject()
            .put("id", journey.id)
            .put("origin", journey.originName)
            .put(
                "destination",
                JSONObject()
                    .put("name", journey.destination.name)
                    .put("lat", journey.destination.latitude)
                    .put("lng", journey.destination.longitude),
            )
            .put("isSample", journey.isSample)
            .put("source", journey.sourceLabel)
            .put("preparedAt", journey.contentPreparedAtEpochMs)
            .put("steps", steps)
    }

    private fun decodeJourney(obj: JSONObject): Journey {
        val stepsJson = obj.getJSONArray("steps")
        val steps = (0 until stepsJson.length()).map { i ->
            val s = stepsJson.getJSONObject(i)
            JourneyStep(
                id = s.getString("id"),
                title = s.getString("title"),
                detail = s.getString("detail"),
                explanation = s.getString("explanation"),
                mode = StepMode.entries.firstOrNull { it.name == s.optString("mode") } ?: StepMode.OTHER,
                caution = if (s.has("caution") && !s.isNull("caution")) s.getString("caution") else null,
            )
        }
        val dest = obj.getJSONObject("destination")
        return Journey(
            id = obj.getString("id"),
            originName = obj.getString("origin"),
            destination = Destination(
                name = dest.getString("name"),
                latitude = dest.getDouble("lat"),
                longitude = dest.getDouble("lng"),
            ),
            steps = steps,
            isSample = obj.getBoolean("isSample"),
            sourceLabel = obj.getString("source"),
            contentPreparedAtEpochMs = obj.getLong("preparedAt"),
        )
    }

    private fun encodeProgress(p: JourneyProgress): JSONObject = JSONObject()
        .put("journeyId", p.journeyId)
        .put("stepIndex", p.stepIndex)
        .put("status", p.status.name)
        .put("startedAt", p.startedAtEpochMs)
        .put("updatedAt", p.updatedAtEpochMs)
        .put("stateVersion", p.stateVersion)

    private fun decodeProgress(obj: JSONObject) = JourneyProgress(
        journeyId = obj.getString("journeyId"),
        stepIndex = obj.getInt("stepIndex"),
        status = ProgressStatus.valueOf(obj.getString("status")),
        startedAtEpochMs = obj.getLong("startedAt"),
        updatedAtEpochMs = obj.getLong("updatedAt"),
        stateVersion = obj.getLong("stateVersion"),
    )
}
