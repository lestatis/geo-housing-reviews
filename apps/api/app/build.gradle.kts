plugins {
    id("geohousing.java-conventions")
    id("org.springframework.boot") version "4.1.0"
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    testImplementation(platform(libs.testcontainers.bom))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
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
    testImplementation(libs.archunit.junit5)
}
