package app.manzil.model

import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Bundled demo content. These steps are illustrative text written for the Manzil demo. They are
 * NOT verified operational transit directions: no timetable, platform, stop pattern or access
 * information has been checked against an authoritative source.
 */
object SampleJourneys {
    const val CHENNAI_CENTRAL_ID = "sample-guindy-to-chennai-central-v1"

    val contentPreparedAtEpochMs: Long = LocalDateTime.of(2026, 11, 5, 12, 0)
        .atZone(ZoneId.of("Asia/Kolkata"))
        .toInstant()
        .toEpochMilli()

    val chennaiCentral: Journey = Journey(
        id = CHENNAI_CENTRAL_ID,
        originName = "Guindy",
        destination = Destination(
            name = "Chennai Central",
            latitude = 13.0827,
            longitude = 80.2752,
        ),
        isSample = true,
        sourceLabel = "Bundled sample written for the Manzil demo. Not verified transit directions.",
        contentPreparedAtEpochMs = contentPreparedAtEpochMs,
        steps = listOf(
            JourneyStep(
                id = "walk-to-guindy-entrance",
                title = "Walk to the Guindy station entrance",
                detail = "Use the entrance on the GST Road side and go up to the suburban platforms.",
                explanation = "This step gets you onto the suburban line. The sample assumes you start " +
                    "near Guindy. If you are already further along, choose the step you are at.",
                mode = StepMode.WALK,
            ),
            JourneyStep(
                id = "board-towards-beach",
                title = "Board a train towards Chennai Beach",
                detail = "Take a northbound suburban train. Follow the platform signs for Beach.",
                explanation = "In this sample, northbound trains on this line pass Park station, which " +
                    "is beside Chennai Central. The saved text cannot know which train or platform is " +
                    "running today.",
                mode = StepMode.TRAIN,
                caution = "Service times and platforms are unverified.",
            ),
            JourneyStep(
                id = "alight-at-park",
                title = "Get off at Park station",
                detail = "Park comes after Egmore. Check the station name boards before you step off.",
                explanation = "Counting stations from a saved list can drift if a train skips stops, so " +
                    "trust the station boards over this list.",
                mode = StepMode.TRAIN,
                caution = "Stop pattern is sample data.",
            ),
            JourneyStep(
                id = "walk-subway-to-central",
                title = "Walk through the subway to Chennai Central",
                detail = "Follow signs for Chennai Central through the pedestrian subway.",
                explanation = "The subway avoids crossing the main road above. Manzil cannot confirm " +
                    "whether it is open or step-free today.",
                mode = StepMode.WALK,
                caution = "Access may differ on the day.",
            ),
            JourneyStep(
                id = "arrive-central-concourse",
                title = "Arrive at the Chennai Central concourse",
                detail = "Check the departure boards for your platform.",
                explanation = "This is the end of the saved journey. Platform information comes only " +
                    "from the station's own boards and staff.",
                mode = StepMode.WALK,
            ),
        ),
    )
}
