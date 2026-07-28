plugins {
    id("geohousing.java-conventions")
    id("geohousing.mutation-testing")
}

dependencies {
    implementation(project(":modules:shared-kernel"))

    // The first cross-module dependency in the codebase: a review points at a property, so reviews
    // asks the properties module about it. Only com.example.geohousing.properties.api may be used —
    // ModuleBoundaryArchitectureTest fails the build if anything reaches the internals.
    implementation(project(":modules:properties"))

    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-web")

    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    testImplementation("org.assertj:assertj-core")
}

mutationTesting {
    mutationThreshold.set(85)
}
