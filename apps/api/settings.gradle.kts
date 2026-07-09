rootProject.name = "geo-housing-api"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(
    "app",
    "modules:shared-kernel",
    "modules:identity",
    "modules:properties",
    "modules:reviews",
    "modules:verification",
    "modules:moderation",
    "modules:media",
    "modules:search",
    "modules:notifications",
    "modules:analytics",
)
