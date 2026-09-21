import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/**
 * Per-module mutation-testing settings.
 *
 * The threshold is deliberately a measured floor rather than an aspiration: a bar nobody can clear
 * gets switched off, which is worse than a modest one that holds. Raise it when a chunk leaves the
 * score above it.
 */
abstract class MutationTestingExtension {

    /** Build fails below this mutation score (percent). */
    abstract val mutationThreshold: Property<Int>

    /** Build fails below this line coverage of the mutated classes (percent). */
    abstract val coverageThreshold: Property<Int>

    /**
     * Globs of classes to mutate. Defaults to the module's framework-free layers; infrastructure is
     * excluded because mutants in Spring/JPA glue are mostly equivalent or untestable, and a score
     * dominated by noise is one nobody acts on.
     */
    abstract val targetClasses: ListProperty<String>

    /**
     * Comma-separated globs of tests allowed to kill the mutants. Defaults to the framework-free
     * domain and application test packages, matching the layers PITest mutates. Infrastructure
     * integration tests stay in the normal test gate but must not start external services during
     * mutation analysis. Modules with a different package layout override this explicitly.
     * Getting this wrong is silent: no test matches, nothing is killed, and the score reads 0%
     * rather than failing with anything that mentions test selection.
     */
    abstract val targetTests: Property<String>
}
