plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":effects"))
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.test)
}

/**
 * Renders the showcase film offline, frame by frame.
 * `./gradlew :film:render -Pshots=title,orbs -Pquality=preview`
 */
tasks.register<JavaExec>("render") {
    group = "film"
    mainClass = "com.burkido.kraft.film.MainKt"
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = rootDir
    maxHeapSize = "6g"
    args(
        "--shots", (project.findProperty("shots") as String?) ?: "all",
        "--quality", (project.findProperty("quality") as String?) ?: "preview",
        "--voice", (project.findProperty("voice") as String?) ?: "onyx",
    )
}

/** Writes the runtime classpath to build/classpath.txt, for rendering shots in parallel JVMs (film/tools/render.sh). */
tasks.register("writeClasspath") {
    group = "film"
    val cp = sourceSets.main.get().runtimeClasspath
    val out = layout.buildDirectory.file("classpath.txt")
    dependsOn(tasks.named("classes"))
    inputs.files(cp)
    outputs.file(out)
    doLast { out.get().asFile.writeText(cp.asPath) }
}
