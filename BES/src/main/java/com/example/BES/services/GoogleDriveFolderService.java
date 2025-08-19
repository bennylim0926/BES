package com.example.BES.services;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.example.BES.dtos.GoogleDriveFolderDto;
import com.google.api.services.drive.model.File;

@Service
public class GoogleDriveFolderService {
    @Autowired
    GoogleDriveManager manager;

    public List<GoogleDriveFolderDto> findAll(){
        List<File> folders = manager.findAllFoldersInFolderById("1jITOoMaZSKQndp2nsnsFATEoF8tJkIj3");
        List<GoogleDriveFolderDto> dtos = new ArrayList<>();

        if(folders == null) return dtos;
        folders.forEach(folder -> {
            if (folder.getSize() == null) {
                GoogleDriveFolderDto dto = new GoogleDriveFolderDto();
                dto.setFolderID(folder.getId());
                dto.setFolderName(folder.getName());
                dto.setUrl("https://drive.google.com/drive/u/0/folders/" + folder.getId());
                dtos.add(dto);
            }
        });
        return dtos;
    }
}