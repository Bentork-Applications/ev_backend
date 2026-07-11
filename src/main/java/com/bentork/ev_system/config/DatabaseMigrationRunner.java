package com.bentork.ev_system.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseMigrationRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        log.info("Running automatic database migrations...");
        
        try {
            jdbcTemplate.execute("ALTER TABLE user_support_requests MODIFY COLUMN attachment_url LONGTEXT");
            log.info("Successfully updated user_support_requests attachment_url to LONGTEXT");
        } catch (Exception e) {
            log.warn("Could not alter user_support_requests (it might already be updated or column doesn't exist yet): {}", e.getMessage());
        }
        
        try {
            jdbcTemplate.execute("ALTER TABLE dealer_support_requests MODIFY COLUMN attachment_url LONGTEXT");
            log.info("Successfully updated dealer_support_requests attachment_url to LONGTEXT");
        } catch (Exception e) {
            log.warn("Could not alter dealer_support_requests (it might already be updated or column doesn't exist yet): {}", e.getMessage());
        }
    }
}
