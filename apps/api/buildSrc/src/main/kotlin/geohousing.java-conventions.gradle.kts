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
}

tasks.named("check") {
    dependsOn(tasks.named("spotlessCheck"))
}
