package com.example.BES.config;

import java.io.FileInputStream;
import java.util.Collections;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GoogleDriveConfig implements GoogleServiceFactory {
    
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
}
