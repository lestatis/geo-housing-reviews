plugins {
    java
}

// PITest runs through its command-line entry point rather than the Gradle plugin. The newest
// gradle-pitest-plugin (1.15.0) cannot be applied on Gradle 9 at all — it reads
// `reporting.baseDir`, which Gradle 9 removed — so this drives the CLI from a JavaExec task.
// See ADR-0009.
//
// Versions are hardcoded for the same reason as in geohousing.java-conventions: buildSrc
// precompiled script plugins cannot reliably resolve the `libs` type-safe accessor. Keep in sync
// with gradle/libs.versions.toml [versions] pitest, pitestJunit5 and junit.
val pitestVersion = "1.25.8"
val pitestJunit5Version = "1.2.3"
val junitVersion = "6.1.1"

val mutationTesting = extensions.create<MutationTestingExtension>("mutationTesting")
mutationTesting.mutationThreshold.convention(0)
mutationTesting.coverageThreshold.convention(0)
mutationTesting.targetTests.convention("com.example.geohousing.${project.name}.*")
mutationTesting.targetClasses.convention(
    listOf(
        "com.example.geohousing.${project.name}.domain.*",
        "com.example.geohousing.${project.name}.application.*",
    ),
)

val pitestCli = configurations.create("pitestCli") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    pitestCli("org.pitest:pitest-command-line:$pitestVersion")
    // Discovers JUnit Platform tests. Built against platform 1.9.2 yet it drives this project's
    // JUnit 6 tests correctly; that compatibility is incidental, so re-verify it on any JUnit or
    // PITest upgrade (ADR-0009).
    pitestCli("org.pitest:pitest-junit5-plugin:$pitestJunit5Version")
    pitestCli("org.junit.jupiter:junit-jupiter:$junitVersion")
    pitestCli("org.junit.platform:junit-platform-launcher")
}

// Resolved against the project here on purpose: inside the task-configuration block the receiver is
// the task, whose extension container holds no SourceSetContainer.
val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
val mainSourceSet = sourceSets.getByName("main")
val testSourceSet = sourceSets.getByName("test")
val pitestReportDir = project.layout.buildDirectory.dir("reports/pitest")

val mutationTest = tasks.register<JavaExec>("mutationTest") {
    group = "verification"
    description = "Mutates ${project.name}'s domain and application layers and fails below the threshold."

    dependsOn(tasks.named("testClasses"))
    outputs.dir(pitestReportDir)

    classpath = pitestCli + mainSourceSet.runtimeClasspath + testSourceSet.runtimeClasspath
    mainClass.set("org.pitest.mutationtest.commandline.MutationCoverageReport")

    argumentProviders.add(
        CommandLineArgumentProvider {
            listOf(
                "--reportDir", pitestReportDir.get().asFile.absolutePath,
                "--targetClasses", mutationTesting.targetClasses.get().joinToString(","),
                "--targetTests", mutationTesting.targetTests.get(),
                // The target glob matches anything sharing the package, so without this PITest
                // mutates the tests and their in-memory doubles too. Mutating a fake measures
                // nothing about the production code and quietly pads the denominator.
                // Globs match the fully-qualified name, so the leading wildcard is required.
                "--excludedClasses", "*Test,*Test\$*,*IT,*IT\$*,*.InMemory*,*.Fake*",
                "--sourceDirs", mainSourceSet.java.srcDirs.joinToString(",") { it.absolutePath },
                "--outputFormats", "XML,HTML",
                "--mutationThreshold", mutationTesting.mutationThreshold.get().toString(),
                "--coverageThreshold", mutationTesting.coverageThreshold.get().toString(),
                "--threads", "4",
                "--timestampedReports", "false",
                "--verbose", "false",
            )
        },
    )
}

// Part of `check`, so the gate runs in ./scripts/check.sh rather than being a task someone
// remembers. `-PskipMutation` opts out for fast local iteration only.
if (!project.hasProperty("skipMutation")) {
    tasks.named("check") {
        dependsOn(mutationTest)
    }
}
