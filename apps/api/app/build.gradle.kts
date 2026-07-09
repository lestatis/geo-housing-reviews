plugins {
    id("geohousing.java-conventions")
    id("org.springframework.boot") version "4.1.0"
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

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
    testImplementation(libs.archunit.junit5)
}
