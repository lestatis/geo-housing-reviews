plugins {
    id("geohousing.java-conventions")
    id("geohousing.mutation-testing")
}

dependencies {
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    testImplementation("org.assertj:assertj-core")
}

mutationTesting {
    // Two value types with validation and an ordering, fully covered. The bar is the score, and it
    // should stay there: anything landing here that cannot be killed does not belong in a module
    // every other module depends on.
    mutationThreshold.set(100)
    targetClasses.set(listOf("com.example.geohousing.shared.*"))
    // The package is `shared`, not `shared-kernel`; without this no test matches and the score
    // reads 0% rather than saying nothing was selected.
    targetTests.set("com.example.geohousing.shared.*")
}
