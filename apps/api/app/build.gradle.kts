plugins {
    id("geohousing.java-conventions")
}

dependencies {
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

    testImplementation(libs.archunit.junit5)
}
