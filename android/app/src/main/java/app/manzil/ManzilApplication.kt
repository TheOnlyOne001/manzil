package app.manzil

import android.app.Application
import android.content.Context
import app.manzil.agent.JourneyController
import app.manzil.data.JourneyRepository
import app.manzil.data.SharedPreferencesStore
import app.manzil.system.AppSettings
import app.manzil.system.SpeechOutput

class ManzilApplication : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }
}

/** Process-wide singletons shared by the activity and the overlay service. */
class AppGraph(app: Application) {
    val settings = AppSettings(app.getSharedPreferences("manzil_settings", Context.MODE_PRIVATE))
    val repository = JourneyRepository(
        SharedPreferencesStore(app.getSharedPreferences("manzil_journeys", Context.MODE_PRIVATE)),
    )
    val controller = JourneyController(repository)
    val speech = SpeechOutput(app, settings)
}

val Context.appGraph: AppGraph
    get() = (applicationContext as ManzilApplication).graph
