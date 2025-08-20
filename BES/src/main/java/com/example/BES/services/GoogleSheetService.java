package com.example.BES.services;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BES.config.GoogleSheetConfig;
import com.example.BES.dtos.GoogleSheetFileDto;
import com.google.api.services.sheets.v4.model.BatchGetValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;

@Service
public class GoogleSheetService {
    @Autowired
    GoogleSheetConfig config;

    List<String> ranges = Arrays.asList("F:F", "H:H");
    public GoogleSheetFileDto getSheetInformationById(String fileId) throws IOException{
        GoogleSheetFileDto dto = new GoogleSheetFileDto();
        BatchGetValuesResponse response = config
                                .getSheets()
                                .spreadsheets()
                                .values()
                                .batchGet(fileId)
                                .setRanges(ranges)
                                // If I am the one manage the form is always gonna be this
                                // .get(fileId, "F:F")
                                .execute();

        List<ValueRange> valueRanges = response.getValueRanges();

        List<List<Object>> colF = valueRanges.get(0).getValues();
        List<List<Object>> colH = valueRanges.get(1).getValues();
        List<String> combined = new ArrayList<>();
        for(int i = 1; i < colF.size(); i++){
            // combined.addAll(colF.get(i));
            // combined.get(i).addAll(colH.get(i));
            if(colF.get(i).size() == 0){
                combined.add(colH.get(i).get(0).toString());
            }else{
                combined.add(colF.get(i).get(0).toString());
            }
        }
        dto.setPopping(Collections.frequency(combined, "Popping 1v1"));
        dto.setWaacking(Collections.frequency(combined, "Waacking 1v1"));
        dto.setLocking(Collections.frequency(combined, "Locking 1v1"));
        dto.setOpen(Collections.frequency(combined, "Open 1v1"));
        dto.setAudience(Collections.frequency(combined, "Audience"));
        return dto;
    }
}