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

    // In :app every integration test class shares one PostgreSQL container per test JVM
    // (SharedPostgis), which empties the application tables before each class. Each fork is its own
    // JVM, so it starts its own shared container: forks run classes side by side and never share a
    // database. The migration-history split test and the Cucumber suite keep their own containers.
    //
    // Deliberately a small fraction of the available processors, not all of them: every fork holds
    // a database container, and starving the machine of memory makes the suite slower, not faster.
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 5).coerceIn(1, 4)
}

tasks.named("check") {
    dependsOn(tasks.named("spotlessCheck"))
}
