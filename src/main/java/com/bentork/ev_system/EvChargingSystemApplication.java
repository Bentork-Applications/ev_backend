
// package com.bentork.ev_system;

// import org.springframework.boot.SpringApplication;
// import org.springframework.boot.autoconfigure.SpringBootApplication;
// import io.github.cdimascio.dotenv.Dotenv;

// @SpringBootApplication
// public class EvChargingSystemApplication {

//     public static void main(String[] args) {
//         // Load .env file before starting the app
//         Dotenv dotenv = Dotenv.configure()
//                 .directory("./") // Location of .env (root folder)
//                 .ignoreIfMalformed()
//                 .ignoreIfMissing()
//                 .load();

//         // Set them as system properties so Spring Boot can use ${...}
//         System.setProperty("GOOGLE_CLIENT_ID", dotenv.get("GOOGLE_CLIENT_ID"));
//         System.setProperty("GOOGLE_CLIENT_SECRET", dotenv.get("GOOGLE_CLIENT_SECRET"));

//         SpringApplication.run(EvChargingSystemApplication.class, args);
//     }
// }
package com.bentork.ev_system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import io.github.cdimascio.dotenv.Dotenv;

@SpringBootApplication
public class EvChargingSystemApplication {

    public static void main(String[] args) {
        // 1. Try to load .env file, but don't crash if missing (Cloud Safe)
        Dotenv dotenv = Dotenv.configure()
                .directory("./")
                .ignoreIfMalformed()
                .ignoreIfMissing()
                .load();

        // 2. Safely set System Properties only if found in .env
        // On the server, these will be skipped, and Spring will use the Service File
        // instead.
        setSystemProperty(dotenv, "GOOGLE_CLIENT_ID");
        setSystemProperty(dotenv, "GOOGLE_CLIENT_SECRET");
        setSystemProperty(dotenv, "RAZORPAY_KEY_ID");
        setSystemProperty(dotenv, "RAZORPAY_KEY_SECRET");

        SpringApplication.run(EvChargingSystemApplication.class, args);
    }

    // Helper method to avoid repetitive null checks
    private static void setSystemProperty(Dotenv dotenv, String key) {
        String value = dotenv.get(key);
        if (value != null) {
            System.setProperty(key, value);
        }
    }
}