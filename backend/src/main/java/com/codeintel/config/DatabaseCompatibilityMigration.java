package com.codeintel.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Repairs schema details created by earlier Phase 3/4 builds.
 * This keeps an existing demo database compatible after deployment without
 * requiring the user to delete their PostgreSQL database.
 */
@Component
@Order(1)
public class DatabaseCompatibilityMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseCompatibilityMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        jdbcTemplate.execute("""
            DO $$
            BEGIN
                IF EXISTS (
                    SELECT 1 FROM information_schema.tables
                    WHERE table_schema = current_schema() AND table_name = 'projects'
                ) THEN
                    -- Normalize rows created by an older build before tightening the CHECK.
                    UPDATE projects SET status = 'READY' WHERE status = 'ANALYZED';

                    ALTER TABLE projects DROP CONSTRAINT IF EXISTS projects_status_check;
                    ALTER TABLE projects
                        ADD CONSTRAINT projects_status_check
                        CHECK (status IN ('CREATED','INGESTING','ANALYZING','READY','FAILED'));

                    ALTER TABLE projects
                        ALTER COLUMN parse_errors TYPE TEXT
                        USING parse_errors::text;

                    ALTER TABLE projects
                        ALTER COLUMN unresolved_references TYPE TEXT
                        USING unresolved_references::text;

                    ALTER TABLE projects
                        ALTER COLUMN error_message TYPE TEXT
                        USING error_message::text;
                END IF;
            END $$;
            """);
    }
}
