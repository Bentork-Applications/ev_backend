package com.bentork.ev_system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import io.github.cdimascio.dotenv.Dotenv;

@SpringBootApplication
public class EvChargingSystemApplication {

    public static void main(String[] args) {
        // 1. Load .env file (if it exists)
        // This configuration prevents crashing if the file is missing
        Dotenv dotenv = Dotenv.configure()
                .directory("./")
                .ignoreIfMalformed()
                .ignoreIfMissing()
                .load();

        // 2.SAFE SETTING: Only set system property if the value exists in .env
        // On AWS, these will be skipped (which is good!)
        safeSetProperty(dotenv, "GOOGLE_CLIENT_ID");
        safeSetProperty(dotenv, "GOOGLE_CLIENT_SECRET");

        SpringApplication.run(EvChargingSystemApplication.class, args);
    }

    // Helper method to stop the NullPointerException
    private static void safeSetProperty(Dotenv dotenv, String key) {
        String value = dotenv.get(key);
        if (value != null && !value.isEmpty()) {
            System.setProperty(key, value);
        }
    }
}