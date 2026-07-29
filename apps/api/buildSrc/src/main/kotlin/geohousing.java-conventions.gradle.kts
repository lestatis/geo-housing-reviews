import com.diffplug.gradle.spotless.SpotlessExtension

plugins {
    java
    checkstyle
    id("com.diffplug.spotless")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

checkstyle {
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
    isIgnoreFailures = false
}

configure<SpotlessExtension> {
    java {
        target("src/**/*.java")
        googleJavaFormat()
    }
}

// Hardcoded rather than read from the root version catalog: buildSrc precompiled
// script plugins cannot reliably resolve the `libs` type-safe accessor. Keep in
// sync with gradle/libs.versions.toml [versions] junit.
dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()

    // Most integration test classes start their own PostgreSQL container, so the suite spends its
    // time waiting on container startup rather than on CPU. Running a few test JVMs side by side
    // overlaps that waiting. Isolation is unchanged: each class already owns its own container, so
    // forking changes only how many wait at once.
    //
    // Deliberately a small fraction of the available processors, not all of them: every fork can
    // hold a database container, and starving the machine of memory makes the suite slower, not
    // faster.
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 5).coerceIn(1, 4)
}

tasks.named("check") {
    dependsOn(tasks.named("spotlessCheck"))
}
