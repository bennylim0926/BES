package com.example.BES.config;

import java.io.FileInputStream;
import java.util.Collections;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;

@Configuration
public class GoogleSheetConfig implements GoogleServiceFactory{

    @Bean
     public Sheets getSheets(){
        try{
            GoogleCredential credential = GoogleCredential.fromStream(new FileInputStream(CREDENTIALS_FILE_PATH))
            .createScoped(Collections.singleton(SheetsScopes.SPREADSHEETS_READONLY));
            return new Sheets.Builder(GoogleNetHttpTransport.newTrustedTransport(),
            JSON_FACTORY,
            credential)
            .build();
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }
}
