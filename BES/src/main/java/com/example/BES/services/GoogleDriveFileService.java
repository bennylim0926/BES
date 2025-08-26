package com.example.BES.services;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.example.BES.dtos.GoogleDriveFileDto;
import com.google.api.services.drive.model.File;

@Service
public class GoogleDriveFileService {
    @Autowired
    private GoogleDriveManager manager;

    // Note that every battle event folder should contain only one sheet in this case
    public List<GoogleDriveFileDto> findAllSheetsInFolder(String folderId){
        List<GoogleDriveFileDto> googleDriveFileDTOList = new ArrayList<>();
        List<File> files = manager.findAllFilesInFolderById(folderId);

        if (files == null) return googleDriveFileDTOList;

        files.forEach(file -> {
            if (file.getSize() != null && file.getMimeType().equals("application/vnd.google-apps.spreadsheet")) {
                GoogleDriveFileDto driveFileDto = new GoogleDriveFileDto();
                fillGoogleDriveFileDTOList(googleDriveFileDTOList, file, driveFileDto);
            }
        });

        return googleDriveFileDTOList;
    }

    private void fillGoogleDriveFileDTOList(List<GoogleDriveFileDto> googleDriveFileDTOS, File file, GoogleDriveFileDto driveFileDto) {
        driveFileDto.setFileId(file.getId());
        driveFileDto.setFileName(file.getName());
        driveFileDto.setFileType(file.getMimeType());
        driveFileDto.setThumbnailLink(file.getThumbnailLink());
        driveFileDto.setSize(String.valueOf(file.getSize()));
        driveFileDto.setLink("https://drive.google.com/file/d/" + file.getId() + "/view?usp=sharing");
        googleDriveFileDTOS.add(driveFileDto);
    }
}