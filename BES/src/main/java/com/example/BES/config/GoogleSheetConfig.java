package com.example.BES.config;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Collections;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;

@Configuration
@Profile("!test")
public class GoogleSheetConfig implements GoogleServiceFactory {

    @Bean
    public Sheets getSheets() {
        try {
            GoogleCredential credential = GoogleCredential.fromStream(new FileInputStream(CREDENTIALS_FILE_PATH))
                .createScoped(Collections.singleton(SheetsScopes.SPREADSHEETS));
            return new Sheets.Builder(GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY, credential)
                .setApplicationName("kyrove")
                .build();
        } catch (FileNotFoundException e) {
            System.err.println("WARN: credentials.json not available — Google Sheets integration disabled (" + e.getMessage() + ")");
            return buildPlaceholderSheets();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Sheets buildPlaceholderSheets() {
        try {
            return new Sheets.Builder(GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY, request -> { })
                .setApplicationName("kyrove-placeholder")
                .build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
