package com.minidoodle.architecture;

import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * The architecture rules, as data.
 *
 * <p>Each rule is a single {@link ArchRule} value so it can be both <em>enforced</em> (in {@link
 * ArchitectureTest}, over the production classpath) and <em>proven effective</em> (in {@link
 * ArchitectureRuleEffectivenessTest}, over deliberately violating fixtures). A rule that is only
 * enforced can be vacuously green forever if its package pattern is wrong; a rule that is also
 * asserted to fail on a known violation cannot.
 *
 * <p>Rules are written against {@code ..domain..}-style package patterns rather than the concrete
 * {@code com.minidoodle...} names so that the same rule matches both the production tree and the
 * fixture tree. The one exception is R6, whose slice pattern is anchored at the context package on
 * purpose - see its own comment.
 */
final class ArchitectureRules {

    private ArchitectureRules() {}

    /** R1 - the domain is framework-free. */
    static final ArchRule DOMAIN_IS_FRAMEWORK_FREE =
            noClasses()
                    .that()
                    .resideInAPackage("..domain..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework..", "jakarta..", "org.jooq..", "org.hibernate..")
                    .because("the domain must not depend on frameworks or persistence");

    /** R2 - the domain is the innermost layer: it depends on the JDK and itself, nothing else. */
    static final ArchRule DOMAIN_DEPENDS_ON_NOTHING_BUT_ITSELF =
            classes()
                    .that()
                    .resideInAPackage("..domain..")
                    .should()
                    .onlyDependOnClassesThat()
                    .resideInAnyPackage("java..", "..domain..")
                    .because("dependencies point inward, and the domain is the innermost layer");

    /** R3 - the application layer must not depend on adapters. */
    static final ArchRule APPLICATION_DOES_NOT_DEPEND_ON_ADAPTERS =
            noClasses()
                    .that()
                    .resideInAPackage("..application..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..adapter..")
                    .because("the application layer talks to ports, never to adapters");

    /** Application dependencies are closed except for the deliberate Spring wiring compromise. */
    static final ArchRule APPLICATION_DEPENDENCIES_ARE_CLOSED =
            classes()
                    .that()
                    .resideInAPackage("..application..")
                    .should()
                    .onlyDependOnClassesThat()
                    .resideInAnyPackage(
                            "java..",
                            "..application..",
                            "..domain..",
                            "org.springframework.stereotype..",
                            "org.springframework.transaction.annotation..");

    /** Web adapters call application ports rather than concrete services or outbound ports. */
    static final ArchRule INBOUND_USES_APPLICATION_INPUT_PORTS =
            noClasses()
                    .that()
                    .resideInAPackage("..adapter.in..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..application.service..", "..application.port.out..");

    /** Outbound adapters must not reach into inbound delivery mechanisms. */
    static final ArchRule OUTBOUND_DOES_NOT_DEPEND_ON_INBOUND =
            noClasses()
                    .that()
                    .resideInAPackage("..adapter.out..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..adapter.in..");

    /** R4 - inbound and outbound adapters must not depend on each other. */
    static final ArchRule ADAPTERS_DO_NOT_DEPEND_ON_EACH_OTHER =
            noClasses()
                    .that()
                    .resideInAPackage("..adapter.in..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..adapter.out..")
                    .because("adapters are siblings: they meet in the application layer");

    /** R5 - shared infrastructure must not depend on the application or the adapters. */
    static final ArchRule SHARED_DOES_NOT_DEPEND_ON_APPLICATION_OR_ADAPTERS =
            noClasses()
                    .that()
                    .resideInAPackage("..shared..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..application..", "..adapter..")
                    .because("shared infrastructure is a leaf: it may depend on the domain only");

    /**
     * R6 - the context's packages form no dependency cycle.
     *
     * <p>The slice pattern deliberately starts at the context package and uses {@code (**)} so that
     * each *leaf* package is its own slice. Slicing at the top level of {@code com.minidoodle}
     * instead would flag the legitimate {@code calendar} &harr; {@code shared} pair (inbound
     * adapters use the shared caller resolver; the shared exception advice maps domain exceptions)
     * as a cycle, which is a false positive - once {@code shared.web} is read as another adapter,
     * the dependency only ever points inward.
     *
     * <p>{@code shared} is not inside this pattern because it needs no cycle check of its own: R5
     * already forbids it from depending on the application or on any adapter, and R2 forbids the
     * domain from depending on it, so no cycle through {@code shared} can exist.
     *
     * <p>Like every other rule here, this one is proven effective against a real cycle rather than
     * trusted: {@code com.minidoodle.calendar.fixture.cyclic.a} and {@code ...b} reference each
     * other and the rule is asserted to report them.
     */
    static final ArchRule THE_CONTEXT_HAS_NO_PACKAGE_CYCLES =
            slices().matching("com.minidoodle.calendar.(**)..")
                    .should()
                    .beFreeOfCycles()
                    .because(
                            "a cycle means a dependency points outward, which the layering forbids");
}
