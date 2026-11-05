import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.mavenPublish)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()
    jvm()

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    android {
        namespace = "com.burkido.kraft.effects"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        // iOS and desktop both render through Skia (Skiko), so they share one actual.
        val skikoMain by creating {
            dependsOn(commonMain.get())
        }
        iosMain.get().dependsOn(skikoMain)
        jvmMain.get().dependsOn(skikoMain)

        commonMain.dependencies {
            // Compose types (Composable, Modifier, Color, ...) are part of the public API.
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.compose.uiTest)
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "com.burkido.kraft.effects.resources"
}

// Coordinates and POM details come from gradle.properties: GROUP, VERSION_NAME and POM_* at the
// root, POM_ARTIFACT_ID, POM_NAME and POM_DESCRIPTION in this module. Releases pass the version
// from the GitHub release's tag with -PVERSION_NAME, see docs/RELEASING.md.
mavenPublishing {
    // publishAndReleaseToMavenCentral (the Publish workflow) releases right away;
    // publishToMavenCentral (scripts/publish.sh) stops at Validated until you press Publish.
    publishToMavenCentral()

    // Only signed when the key is provided, so publishToMavenLocal works without it.
    // Maven Central rejects unsigned uploads, so a missing key can't slip through.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }
}
