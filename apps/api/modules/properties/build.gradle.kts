plugins {
    id("geohousing.java-conventions")
    id("geohousing.mutation-testing")
}

dependencies {
    implementation(project(":modules:shared-kernel"))

    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // Web layer only (controllers, ProblemDetail). Deliberately no Spring Security dependency:
    // the module reads the caller via the JDK Principal, and security policy lives in the app.
    implementation("org.springframework.boot:spring-boot-starter-web")
    // `@Schema(nullable = true)` on response DTOs. Compile-only: the app already carries the
    // annotations at runtime through springdoc, so this adds nothing to what ships.
    compileOnly(libs.swagger.annotations)

    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    testImplementation("org.assertj:assertj-core")
}

mutationTesting {
    mutationThreshold.set(77)
}
