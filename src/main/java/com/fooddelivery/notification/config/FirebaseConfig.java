package com.fooddelivery.notification.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;

@Configuration
@lombok.extern.slf4j.Slf4j
public class FirebaseConfig {

    @PostConstruct
    public void initialize() {
        try {
            // Check if Firebase is already initialized
            if (FirebaseApp.getApps().isEmpty()) {
                // In production, this path comes from the secret mount: /etc/secrets/firebase/firebase-admin.json
                // We use application default credentials or explicitly provide a path via an env var if needed.
                // For demonstration, we'll try to use GoogleCredentials.getApplicationDefault() 
                // which relies on GOOGLE_APPLICATION_CREDENTIALS environment variable.
                
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.getApplicationDefault())
                        .build();

                FirebaseApp.initializeApp(options);
                log.info("Firebase application has been initialized");
            }
        } catch (IOException e) {
            log.error("Failed to initialize Firebase app. Ensure GOOGLE_APPLICATION_CREDENTIALS is set.", e);
        }
    }
}
