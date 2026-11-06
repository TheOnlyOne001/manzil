import java.security.MessageDigest
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localConfig = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
val mapsKey = providers.environmentVariable("MANZIL_GOOGLE_MAPS_API_KEY").orNull
    ?: localConfig.getProperty("MAPS_API_KEY", "")
val overlayApi = providers.environmentVariable("MANZIL_OVERLAY_API_URL").orNull
    ?: localConfig.getProperty("MANZIL_OVERLAY_API_URL", "")
val agentApi = providers.environmentVariable("MANZIL_AGENT_API_URL").orNull
    ?: localConfig.getProperty("MANZIL_AGENT_API_URL", "")
fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "app.manzil"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.manzil"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-demo"
        manifestPlaceholders["GOOGLE_MAPS_API_KEY"] = mapsKey.ifBlank { "MANZIL_KEY_NOT_CONFIGURED" }
        buildConfigField("boolean", "GOOGLE_MAPS_CONFIGURED", mapsKey.isNotBlank().toString())
        buildConfigField("String", "OVERLAY_API_URL", quoted(overlayApi))
        buildConfigField("String", "AGENT_API_URL", quoted(agentApi))
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions.unitTests.all {
        it.systemProperty("manzil.routing.dir", rootProject.file("../data/offline-routing").absolutePath)
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

/**
 * Bundles the design-approved Manrope font and the Lucide / OFL licence texts from
 * ../design/assets into generated resources, so the provenance files stay single-sourced.
 * If someone places app/src/main/res/font/manrope.ttf by hand, the font copy is skipped
 * (avoids a duplicate-resource merge error); licences are always bundled.
 */
abstract class BundleDesignAssets : DefaultTask() {
    @get:org.gradle.api.tasks.Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val fontFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val licenceFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val resOutput: DirectoryProperty

    @get:OutputDirectory
    abstract val assetsOutput: DirectoryProperty

    @TaskAction
    fun bundle() {
        val resDir = resOutput.get().asFile
        resDir.deleteRecursively()
        resDir.mkdirs()
        if (fontFile.isPresent) {
            val fontDir = File(resDir, "font").apply { mkdirs() }
            fontFile.get().asFile.copyTo(File(fontDir, "manrope.ttf"), overwrite = true)
        }
        val licenceDir = File(assetsOutput.get().asFile, "licenses")
        licenceDir.deleteRecursively()
        licenceDir.mkdirs()
        licenceFiles.files.forEach { source ->
            val name = when (source.name) {
                "LUCIDE-LICENSE" -> "LUCIDE-LICENSE.txt"
                "OFL.txt" -> "MANROPE-OFL.txt"
                else -> source.name
            }
            source.copyTo(File(licenceDir, name), overwrite = true)
        }
    }
}

val designAssets = rootProject.layout.projectDirectory.dir("../design/assets")
val handPlacedFont = layout.projectDirectory.file("src/main/res/font/manrope.ttf").asFile.exists()

androidComponents {
    onVariants { variant ->
        val taskName = "bundle" + variant.name.replaceFirstChar { it.uppercase() } + "DesignAssets"
        val bundleTask = tasks.register<BundleDesignAssets>(taskName) {
            if (!handPlacedFont) {
                fontFile.set(designAssets.file("fonts/manrope/Manrope[wght].ttf"))
            }
            licenceFiles.from(
                designAssets.file("licenses/LUCIDE-LICENSE"),
                designAssets.file("fonts/manrope/OFL.txt"),
            )
        }
        variant.sources.res?.addGeneratedSourceDirectory(bundleTask, BundleDesignAssets::resOutput)
        variant.sources.assets?.addGeneratedSourceDirectory(bundleTask, BundleDesignAssets::assetsOutput)
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-maps:20.1.0")
    // Fused provider for fast first fix when online; raw GPS_PROVIDER remains the offline fallback.
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation(project(":brouter-core"))
    implementation("org.maplibre.gl:android-sdk:11.8.2")
    // Pinned to releases that build against compileSdk 35 (none of these require SDK 36).
    val composeBom = platform("androidx.compose:compose-bom:2025.05.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    testImplementation("junit:junit:4.13.2")
    // android.jar only ships org.json stubs for local JVM tests; use the real implementation there.
    testImplementation("org.json:json:20240303")
}

// Ship the real local map/search/graph, never downloader parts or temporary files.
val offlineAssetsDir = layout.buildDirectory.dir("generated/offlineAssets")
val bundleOfflinePack = tasks.register<Sync>("bundleOfflinePack") {
    into(offlineAssetsDir.map { it.dir("offline-pack") })
    from(rootProject.file("../data/offline-map")) {
        into("offline-map")
        include("*.json", "*.geojson", "*.txt", "tiles/*.geojson")
    }
    from(rootProject.file("../data/offline-routing")) {
        into("offline-routing")
        include("profiles/*.brf", "profiles/*.dat", "segments/*.rd5", "segments/*.rd5.json")
    }
    from(rootProject.file("../third_party/brouter/LICENSE")) { into("licenses") }
    from(rootProject.file("../data/derived/chennai_map_overlays.geojson")) { into("overlays") }
    doLast {
        val base = offlineAssetsDir.get().dir("offline-pack").asFile
        val entries = base.walkTopDown().filter { it.isFile && it.name != "index.tsv" }.sortedBy { it.path }.map { file ->
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(65536)
                while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            "${file.relativeTo(base).invariantSeparatorsPath}\t${file.length()}\t$hash"
        }.toList()
        base.resolve("index.tsv").writeText(entries.joinToString("\n"))
    }
}
android.sourceSets.getByName("main").assets.srcDir(offlineAssetsDir)
tasks.named("preBuild").configure { dependsOn(bundleOfflinePack) }
