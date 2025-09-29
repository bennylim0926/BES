package com.example.BES.services;

import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BES.config.GoogleDriveConfig;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

@Service
public class GoogleDriveManager {
    @Autowired
    private GoogleDriveConfig config;

    public List<com.google.api.services.drive.model.File> findAllFoldersInFolderById(String folderId){
        try{
            String query = String.format("'%s' in parents and mimeType = 'application/vnd.google-apps.folder' and trashed = false", folderId);
            return config.getDrive()
                    .files()
                    .list()
                    .setQ(query)
                    .setSpaces("drive")
                    .setFields("nextPageToken, files(id, name)")
                    .execute().getFiles();

        }catch(IOException e){
            throw new RuntimeException(e);
        }
    }

    public List<File> findAllFilesInFolderById(String folderId) {
        try {
            folderId = folderId == null ? "root" : folderId;
            String query = "'" + folderId + "' in parents and trashed = false";
            FileList result = config.getDrive()
                    .files()
                    .list()
                    .setQ(query)
                    .setPageSize(10)
                    .setFields("nextPageToken, files(id, name, size, thumbnailLink, mimeType, shortcutDetails)")
                    .execute();
            return result.getFiles();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public File findByTargetId(String targetId){
        try{
            File target = config.getDrive()
                .files()
                .get(targetId)
                .setFields("id, name, mimeType, size, thumbnailLink")
                .execute();
            return target;
        }catch(IOException e){
            throw new RuntimeException(e);
        }
        
    }
}