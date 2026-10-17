package com.minidoodle.architecture;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves that each architecture rule actually rejects a violation.
 *
 * <p>Without this, a rule whose package pattern was misspelled - or which silently matches no
 * classes at all - is green forever, and "the architecture is enforced" becomes an assertion rather
 * than a fact. Each fixture in {@code com.minidoodle.architecture.fixture} is a minimal class that
 * breaks exactly one rule; the rule is evaluated against the fixtures alone and must report it.
 *
 * <p>The production run of the very same rule objects is {@link ArchitectureTest}. Because these
 * fixtures live in the test sources, the two runs cannot interfere.
 */
class ArchitectureRuleEffectivenessTest {

    private static final JavaClasses FIXTURES =
            new ClassFileImporter().importPackages("com.minidoodle.architecture.fixture");

    private static final JavaClasses CYCLE_FIXTURES =
            new ClassFileImporter().importPackages("com.minidoodle.calendar.fixture.cyclic");

    @Test
    void theDomainFrameworkRuleRejectsAFrameworkTypeInTheDomain() {
        assertRuleRejects(ArchitectureRules.DOMAIN_IS_FRAMEWORK_FREE, "DomainReachingForSpring");
    }

    @Test
    void theDomainClosureRuleRejectsAnOutwardDependency() {
        assertRuleRejects(
                ArchitectureRules.DOMAIN_DEPENDS_ON_NOTHING_BUT_ITSELF,
                "DomainReachingForApplication");
    }

    @Test
    void theApplicationRuleRejectsADependencyOnAnAdapter() {
        assertRuleRejects(
                ArchitectureRules.APPLICATION_DOES_NOT_DEPEND_ON_ADAPTERS,
                "ApplicationReachingForAdapter");
    }

    @Test
    void theAdapterRuleRejectsAnInboundToOutboundDependency() {
        assertRuleRejects(
                ArchitectureRules.ADAPTERS_DO_NOT_DEPEND_ON_EACH_OTHER,
                "InboundAdapterReachingForOutbound");
    }

    @Test
    void theSharedRuleRejectsADependencyOnTheApplication() {
        assertRuleRejects(
                ArchitectureRules.SHARED_DOES_NOT_DEPEND_ON_APPLICATION_OR_ADAPTERS,
                "SharedReachingForUseCase");
    }

    @Test
    void theCycleRuleRejectsTwoPackagesThatReferenceEachOther() {
        assertRuleRejects(
                ArchitectureRules.THE_CONTEXT_HAS_NO_PACKAGE_CYCLES, "Cycle", CYCLE_FIXTURES);
    }

    private static void assertRuleRejects(ArchRule rule, String offendingType) {
        assertRuleRejects(rule, offendingType, FIXTURES);
    }

    private static void assertRuleRejects(
            ArchRule rule, String offendingType, JavaClasses classes) {
        EvaluationResult result = rule.evaluate(classes);

        assertThat(result.hasViolation())
                .as("rule [%s] should reject %s", rule, offendingType)
                .isTrue();
        assertThat(result.getFailureReport().toString())
                .as("rule [%s] should name %s", rule, offendingType)
                .contains(offendingType);
    }
}
