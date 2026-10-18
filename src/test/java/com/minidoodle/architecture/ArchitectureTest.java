package com.minidoodle.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Enforces the hexagonal layering described in the README and the project constitution.
 *
 * <p>These are ordinary JUnit 5 tests, so they run in the existing {@code test} task and therefore
 * in the {@code ./gradlew build} that the pre-push hook and CI already run - no new task, no new
 * wiring, no new CI step.
 *
 * <p>Tests are excluded from the import on purpose: the deliberately violating fixtures in {@code
 * com.minidoodle.architecture.fixture} would otherwise make every rule fail. Their job is to prove
 * the rules bite when they are pointed at a violation; see {@link
 * ArchitectureRuleEffectivenessTest}.
 */
@AnalyzeClasses(packages = "com.minidoodle", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule DOMAIN_IS_FRAMEWORK_FREE = ArchitectureRules.DOMAIN_IS_FRAMEWORK_FREE;

    @ArchTest
    static final ArchRule DOMAIN_DEPENDS_ON_NOTHING_BUT_ITSELF =
            ArchitectureRules.DOMAIN_DEPENDS_ON_NOTHING_BUT_ITSELF;

    @ArchTest
    static final ArchRule APPLICATION_DOES_NOT_DEPEND_ON_ADAPTERS =
            ArchitectureRules.APPLICATION_DOES_NOT_DEPEND_ON_ADAPTERS;

    @ArchTest
    static final ArchRule ADAPTERS_DO_NOT_DEPEND_ON_EACH_OTHER =
            ArchitectureRules.ADAPTERS_DO_NOT_DEPEND_ON_EACH_OTHER;

    @ArchTest
    static final ArchRule SHARED_DOES_NOT_DEPEND_ON_APPLICATION_OR_ADAPTERS =
            ArchitectureRules.SHARED_DOES_NOT_DEPEND_ON_APPLICATION_OR_ADAPTERS;

    @ArchTest
    static final ArchRule THE_CONTEXT_HAS_NO_PACKAGE_CYCLES =
            ArchitectureRules.THE_CONTEXT_HAS_NO_PACKAGE_CYCLES;

    @ArchTest
    static final ArchRule APPLICATION_DEPENDENCIES_ARE_CLOSED =
            ArchitectureRules.APPLICATION_DEPENDENCIES_ARE_CLOSED;

    @ArchTest
    static final ArchRule INBOUND_USES_APPLICATION_INPUT_PORTS =
            ArchitectureRules.INBOUND_USES_APPLICATION_INPUT_PORTS;

    @ArchTest
    static final ArchRule OUTBOUND_DOES_NOT_DEPEND_ON_INBOUND =
            ArchitectureRules.OUTBOUND_DOES_NOT_DEPEND_ON_INBOUND;
}
