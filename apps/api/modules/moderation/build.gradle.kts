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

    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-web")

    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    testImplementation("org.assertj:assertj-core")
}

mutationTesting {
    mutationThreshold.set(86)
}
