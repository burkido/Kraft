import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
}

compose.desktop {
    application {
        mainClass = "com.burkido.kraft.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "Kraft"
            packageVersion = "1.0.0"
            macOS {
                bundleID = "com.burkido.kraft.desktop"
                infoPlist {
                    extraKeysRawXml = """
                        <key>NSMicrophoneUsageDescription</key>
                        <string>Kraft listens to your voice to drive the Voice glow effect.</string>
                    """.trimIndent()
                }
            }
        }
    }
}
