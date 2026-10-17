buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.jooq:jooq-meta:3.19.35")
        classpath("org.jooq:jooq-codegen:3.19.35")
        classpath("org.postgresql:postgresql:42.7.11")
        classpath("org.flywaydb:flyway-core:11.7.2")
        classpath("org.flywaydb:flyway-database-postgresql:11.7.2")
        classpath("org.testcontainers:postgresql:1.21.4")
    }
}

plugins {
    java
    jacoco
    checkstyle
    id("com.diffplug.spotless") version "8.10.4"
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.minidoodle"
version = "0.0.1-SNAPSHOT"
description = "mini-doodle - meeting scheduling backend"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.jooq:jooq")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.postgresql:postgresql")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    // Architecture conformance rules (feature 006-quality-gates). Test scope only: the rules run
    // as ordinary JUnit 5 tests, so they ride the existing `test` task inside `check`.
    //
    // Pinned to 1.4.1, not the newest. ArchUnit 1.5.x is built against junit-platform-engine
    // 1.14.4, which drags junit-bom 5.14.4 into the graph and resolves junit-platform-launcher to
    // 1.14.4 while Spring Boot's dependency management keeps junit-platform-engine at 1.12.2. The
    // mismatch breaks the Gradle 8.14.3 test worker (NoClassDefFoundError:
    // org/junit/platform/engine/OutputDirectoryCreator). 1.4.1 needs junit-platform-engine 1.12.2,
    // which is exactly the Boot-managed version, so the graph stays consistent.
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// --- Style gate (feature 005-checkstyle-enforcement) -------------------------
// Linting and formatting run as part of `check`, hence as part of `build`, hence as part
// of the `./gradlew build` that .githooks/pre-push and CI already run. No new hook or CI
// wiring is introduced for this.
//
// Division of duties: Spotless owns formatting (indentation, wrapping, whitespace, import
// order, unused imports). Checkstyle owns non-formatting rules (naming, brace/block and
// bug-pattern conventions). See config/checkstyle/checkstyle.xml for the curated ruleset.
checkstyle {
    toolVersion = "14.3.0"
    configFile = file("config/checkstyle/checkstyle.xml")
    isIgnoreFailures = false
    maxErrors = 0
    maxWarnings = 0
}

// The jOOQ output under build/generated-sources/jooq is machine-generated and is added to
// the compile sources by the sourceSets block below; it must never be linted or reformatted.
tasks.withType<Checkstyle>().configureEach {
    exclude("**/com/minidoodle/jooq/**")
}

spotless {
    java {
        // Explicit targets keep the generated jOOQ sources out of the formatter's reach.
        target("src/main/java/**/*.java", "src/test/java/**/*.java")
        // AOSP variant: 4-space indentation and a 100-column limit, matching the existing
        // codebase style. The formatter version is pinned so results are reproducible.
        googleJavaFormat("1.36.0").aosp()
        removeUnusedImports()
        importOrder("com.minidoodle", "java", "javax", "org", "com", "", "\\#")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// --- Coverage (report-only) ---------------------------------------------------
// JaCoCo is intentionally NOT wired into `check`: it produces reports but never fails
// the `./gradlew build` that .githooks/pre-push and CI run. It measures instruction,
// branch, line, cyclomatic-complexity, method, and class coverage.
//
//   ./gradlew jacocoTestReport          # tests + report (report also runs after `test`)
//   open build/reports/jacoco/test/html/index.html
//   XML (machine-readable): build/reports/jacoco/test/jacocoTestReport.xml
jacoco {
    toolVersion = "0.8.15"
}

tasks.named<Test>("test") {
    finalizedBy(tasks.named("jacocoTestReport"))
}

tasks.named<JacocoReport>("jacocoTestReport") {
    // The jOOQ output is machine-generated; it is not ours to cover and would otherwise
    // dominate the denominator and drag every percentage down. JacocoReport has no
    // `excludes` property, so filtering is done by rebuilding the class-dir collection.
    classDirectories.setFrom(
        files(classDirectories.files.map { dir ->
            fileTree(dir) { exclude("com/minidoodle/jooq/**") }
        })
    )
    reports {
        xml.required.set(true)
        csv.required.set(false)
        html.required.set(true)
    }
}

val jooqGeneratedDir = layout.buildDirectory.dir("generated-sources/jooq")

// jOOQ code generation runs against a real PostgreSQL 16 database migrated by Flyway.
// The OSS jOOQ parser cannot parse the PostgreSQL-specific DDL (tstzrange generated
// column, exclusion constraint), and generating from a live database keeps the generated
// code faithful to the schema that is actually the source of truth.
//
// By default a throwaway Testcontainers PostgreSQL is used (requires a Docker daemon on
// the build machine). When JOOQ_CODEGEN_URL is set, that database is used instead, which
// lets container image builds run the generator against a PostgreSQL started inside the
// build container (no Docker-in-Docker).
val jooqCodegen by tasks.registering {
    group = "build"
    description = "Generates jOOQ sources from the Flyway-managed schema (postgres:16 container)"
    inputs.dir(layout.projectDirectory.dir("src/main/resources/db/migration"))
    outputs.dir(jooqGeneratedDir)

    doLast {
        val externalUrl = System.getenv("JOOQ_CODEGEN_URL")?.takeIf { it.isNotBlank() }
        val container = if (externalUrl == null) {
            org.testcontainers.containers.PostgreSQLContainer<Nothing>("postgres:16").also { it.start() }
        } else {
            null
        }

        val jdbcUrl = externalUrl ?: container!!.jdbcUrl
        val jdbcUser = System.getenv("JOOQ_CODEGEN_USER")?.takeIf { it.isNotBlank() }
            ?: container?.username ?: "postgres"
        val jdbcPassword = System.getenv("JOOQ_CODEGEN_PASSWORD")?.takeIf { it.isNotBlank() }
            ?: container?.password ?: "postgres"

        try {
            org.flywaydb.core.Flyway.configure()
                .dataSource(jdbcUrl, jdbcUser, jdbcPassword)
                .locations("filesystem:src/main/resources/db/migration")
                .load()
                .migrate()

            val generator = org.jooq.meta.jaxb.Generator()
                .withDatabase(
                    org.jooq.meta.jaxb.Database()
                        .withName("org.jooq.meta.postgres.PostgresDatabase")
                        .withInputSchema("public")
                        .withExcludes("flyway_schema_history")
                        .withIncludeRoutines(false)
                        .withIncludePackages(false)
                )
                .withTarget(
                    org.jooq.meta.jaxb.Target()
                        .withPackageName("com.minidoodle.jooq")
                        .withDirectory(jooqGeneratedDir.get().asFile.path)
                )

            val configuration = org.jooq.meta.jaxb.Configuration()
                .withJdbc(
                    org.jooq.meta.jaxb.Jdbc()
                        .withUrl(jdbcUrl)
                        .withUser(jdbcUser)
                        .withPassword(jdbcPassword)
                )
                .withGenerator(generator)

            org.jooq.codegen.GenerationTool.generate(configuration)
        } finally {
            container?.stop()
        }
    }
}

sourceSets {
    main {
        java {
            srcDir(jooqGeneratedDir)
        }
    }
}

tasks.named("compileJava") {
    dependsOn(jooqCodegen)
}
