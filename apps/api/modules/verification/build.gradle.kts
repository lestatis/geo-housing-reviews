plugins {
    id("geohousing.java-conventions")
}

dependencies {
    implementation(project(":modules:shared-kernel"))

    // A verification case points at a property, so verification asks the properties module about it
    // through properties.api. Only com.example.geohousing.properties.api may be reached —
    // ModuleBoundaryArchitectureTest fails the build otherwise.
    implementation(project(":modules:properties"))

    // On a decision, verification projects the resulting tier onto the author's review through
    // reviews.api (inbound). The projection is one-way — reviews never depends on verification — so
    // the two modules do not form a cycle.
    implementation(project(":modules:reviews"))

    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    testImplementation("org.assertj:assertj-core")
}
