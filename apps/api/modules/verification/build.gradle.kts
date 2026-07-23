plugins {
    id("geohousing.java-conventions")
}

dependencies {
    implementation(project(":modules:shared-kernel"))

    // A verification case points at a property, so verification asks the properties module about it
    // through properties.api (used from chunk 3). Only com.example.geohousing.properties.api may be
    // reached — ModuleBoundaryArchitectureTest fails the build otherwise.
    implementation(project(":modules:properties"))

    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    testImplementation("org.assertj:assertj-core")
}
