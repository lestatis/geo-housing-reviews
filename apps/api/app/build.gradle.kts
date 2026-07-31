plugins {
    id("geohousing.java-conventions")
    id("org.springframework.boot") version "4.1.0"
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(platform(libs.cucumber.bom))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation(libs.springdoc.openapi.webmvc.ui)
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    implementation(project(":modules:shared-kernel"))
    implementation(project(":modules:identity"))
    implementation(project(":modules:properties"))
    implementation(project(":modules:reviews"))
    implementation(project(":modules:verification"))
    implementation(project(":modules:moderation"))
    implementation(project(":modules:media"))
    implementation(project(":modules:search"))
    implementation(project(":modules:notifications"))
    implementation(project(":modules:analytics"))

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    // Tier 2 evidence integration tests run Postgres and the object store together (ADR-0008).
    testImplementation("org.testcontainers:testcontainers-minio")
    testImplementation(libs.archunit.junit5)

    // Acceptance scenarios (ADR-0009). Cucumber 7.x is built against JUnit Platform 1.x yet
    // discovers and runs this project's JUnit 6 tests; that compatibility is incidental, so
    // re-verify scenario discovery on any JUnit or Cucumber upgrade.
    testImplementation("io.cucumber:cucumber-java")
    testImplementation("io.cucumber:cucumber-spring")
    testImplementation("org.junit.platform:junit-platform-suite")
    testRuntimeOnly("io.cucumber:cucumber-junit-platform-engine")
}

tasks.withType<Test> {
    // Fixed, non-secret pepper so the Spring context boots in tests (a blank pepper fails fast by
    // design). This is a test value only, never a production secret — production injects the real
    // pepper via the IDENTITY_AUTH_SUBJECT_PEPPER environment variable.
    environment("IDENTITY_AUTH_SUBJECT_PEPPER", "test-only-auth-subject-pepper-not-for-production")

    // Tests fork a JVM, so -DupdateOpenApiSpec on the Gradle command line does not reach
    // OpenApiContractIntegrationTest unless it is forwarded explicitly.
    systemProperty("updateOpenApiSpec", providers.systemProperty("updateOpenApiSpec").getOrElse(""))
}
