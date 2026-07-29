plugins {
    id("geohousing.java-conventions")
    id("geohousing.mutation-testing")
}

dependencies {
    implementation(project(":modules:shared-kernel"))

    // Moderation acts on content another module owns. Only com.example.geohousing.reviews.api may
    // be reached — ModuleBoundaryArchitectureTest fails the build if anything touches the
    // internals. The dependency is one-way: reviews never learns moderation exists, so the two do
    // not form a cycle.
    implementation(project(":modules:reviews"))

    // Chunk 1 ships the V6.1 schema only. The dependency on reviews (to apply a decision through
    // reviews.api) arrives with chunk 4, and web with chunk 6 — added when there is code that needs
    // them, as the reviews and verification modules were built.
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    testImplementation("org.assertj:assertj-core")
}

mutationTesting {
    mutationThreshold.set(85)
}
