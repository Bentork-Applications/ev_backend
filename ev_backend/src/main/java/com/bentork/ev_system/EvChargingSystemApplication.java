//package com.bentork.ev_system;
//
//import org.springframework.boot.SpringApplication;
//import org.springframework.boot.autoconfigure.SpringBootApplication;
//
//@SpringBootApplication
//public class EvChargingSystemApplication {
//
//	public static void main(String[] args) {
//		SpringApplication.run(EvChargingSystemApplication.class, args);
//	}
//
//}

package com.bentork.ev_system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import io.github.cdimascio.dotenv.Dotenv;

@SpringBootApplication
public class EvChargingSystemApplication {

    public static void main(String[] args) {
        // Load .env file before starting the app
        Dotenv dotenv = Dotenv.configure()
                .directory("./") // Location of .env (root folder)
                .ignoreIfMalformed()
                .ignoreIfMissing()
                .load();

        // Set them as system properties so Spring Boot can use ${...}
        System.setProperty("GOOGLE_CLIENT_ID", dotenv.get("GOOGLE_CLIENT_ID"));
        System.setProperty("GOOGLE_CLIENT_SECRET", dotenv.get("GOOGLE_CLIENT_SECRET"));

        SpringApplication.run(EvChargingSystemApplication.class, args);
    }
}
