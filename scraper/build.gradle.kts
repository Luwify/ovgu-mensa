import java.util.Properties

// Command-line scraper for the public statistics site (Luwify/ovgu-mensa-stats): fetches every Mensa page with the app's
// repository and parsers from `:core` and writes one JSON file per day and Mensa. Run through `installDist`, see README.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

val appVersion: String = Properties().apply { rootProject.file("version.properties").inputStream().use(::load) }
    .getProperty("version") ?: error("version.properties: no `version`")

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
}

application {
    mainClass.set("de.luwify.mensamd.scraper.MainKt")
    applicationDefaultJvmArgs = listOf("-Dmensamd.version=$appVersion")
}

// The fixtures live with the parser tests of the app.
sourceSets.test { resources.srcDir(rootProject.file("app/src/test/resources")) }

// Same name as the app's unit test task, so `./gradlew testDebugUnitTest` (the project's check, also in release.sh)
// runs these tests too.
tasks.register("testDebugUnitTest") { dependsOn(tasks.test) }

dependencies {
    implementation(project(":core"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
