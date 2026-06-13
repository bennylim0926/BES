package com.example.BES.config;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Collections;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class GoogleDriveConfig implements GoogleServiceFactory {

    @Bean
    public Drive getDrive() {
        try {
            GoogleCredential credential = GoogleCredential.fromStream(new FileInputStream(CREDENTIALS_FILE_PATH))
                .createScoped(Collections.singleton(DriveScopes.DRIVE));
            return new Drive.Builder(GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY, credential)
                .setApplicationName("kyrove")
                .build();
        } catch (FileNotFoundException e) {
            // credentials.json missing (or a directory placeholder created by a Docker bind mount).
            // Boot without Google Drive features; calls fail at use time rather than crashing startup.
            System.err.println("WARN: credentials.json not available — Google Drive integration disabled (" + e.getMessage() + ")");
            return buildPlaceholderDrive();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Drive buildPlaceholderDrive() {
        try {
            return new Drive.Builder(GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY, request -> { })
                .setApplicationName("kyrove-placeholder")
                .build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
