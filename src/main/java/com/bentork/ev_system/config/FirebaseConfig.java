package com.bentork.ev_system.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import javax.annotation.PostConstruct;
import java.io.IOException;

@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Value("${firebase.service-account.path}")
    private Resource serviceAccountResource;

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        // Prevent re-initialization error during hot-reloads or tests
        if (FirebaseApp.getApps().isEmpty()) {

            if (!serviceAccountResource.exists()) {
                throw new IOException("Firebase service account file not found: " + serviceAccountResource);
            }

            log.info("Initializing Firebase Admin SDK...");

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccountResource.getInputStream()))
                    .build();

            FirebaseApp app = FirebaseApp.initializeApp(options);
            log.info("Firebase Application '{}' initialized successfully.", app.getName());
            return app;
        }

        return FirebaseApp.getInstance();
    }
}