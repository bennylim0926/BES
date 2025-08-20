package com.example.BES.dtos;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GoogleDriveFileDto {
    private String fileId;
    private String fileName;
    private String fileType;
    private String link;
    private String size;
    private String thumbnailLink;
}