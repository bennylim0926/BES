package com.example.BES.config;

import java.io.FileInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GoogleDriveConfig {
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String CREDENTIALS_FILE_PATH = getPathToGoogleCredentials();
    
    @Bean
    public Drive getDrive(){
        try{
            GoogleCredential credential = GoogleCredential.fromStream(new FileInputStream(CREDENTIALS_FILE_PATH))
            .createScoped(Collections.singleton(DriveScopes.DRIVE));
            return new Drive.Builder(GoogleNetHttpTransport.newTrustedTransport(),
            JSON_FACTORY,
            credential)
            .build();
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }

    private static String getPathToGoogleCredentials(){
        String currentDirectory = System.getProperty("user.dir");
        Path filePath = Paths.get(currentDirectory,"credentials.json");
        return filePath.toString();
    }
}
