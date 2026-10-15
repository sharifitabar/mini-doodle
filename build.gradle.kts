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

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

val jooqGeneratedDir = layout.buildDirectory.dir("generated-sources/jooq")

// jOOQ code generation runs against a real PostgreSQL 16 container migrated by Flyway.
// The OSS jOOQ parser cannot parse the PostgreSQL-specific DDL (tstzrange generated
// column, exclusion constraint), and generating from a live database keeps the generated
// code faithful to the schema that is actually the source of truth.
val jooqCodegen by tasks.registering {
    group = "build"
    description = "Generates jOOQ sources from the Flyway-managed schema (postgres:16 container)"
    inputs.dir(layout.projectDirectory.dir("src/main/resources/db/migration"))
    outputs.dir(jooqGeneratedDir)

    doLast {
        val container = org.testcontainers.containers.PostgreSQLContainer<Nothing>("postgres:16")
        container.start()
        try {
            org.flywaydb.core.Flyway.configure()
                .dataSource(container.jdbcUrl, container.username, container.password)
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
                        .withUrl(container.jdbcUrl)
                        .withUser(container.username)
                        .withPassword(container.password)
                )
                .withGenerator(generator)

            org.jooq.codegen.GenerationTool.generate(configuration)
        } finally {
            container.stop()
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
