package com.example.BES.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;

public interface GoogleServiceFactory {
    JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    String CREDENTIALS_FILE_PATH = getPathToGoogleCredentials();

    private static String getPathToGoogleCredentials(){
        String currentDirectory = System.getProperty("user.dir");
        Path filePath = Paths.get(currentDirectory,"credentials.json");
        return filePath.toString();
    }
    
} 
