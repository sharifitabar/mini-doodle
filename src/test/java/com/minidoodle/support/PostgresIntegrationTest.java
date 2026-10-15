package com.minidoodle.support;

import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Base class for integration tests. Boots the full application context against a
 * throwaway PostgreSQL 16 container (started lazily by the Testcontainers JDBC driver
 * configured in {@code application-test.yml}) with Flyway migrations applied.
 *
 * <p>Each test runs in a rolled-back transaction so tests stay isolated.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public abstract class PostgresIntegrationTest {

    @Autowired
    protected DSLContext dsl;
}
