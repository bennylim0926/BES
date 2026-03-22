package com.example.BES.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.BES.dtos.AddParticipantDto;
import com.example.BES.enums.SheetHeader;
import com.example.BES.parsers.GoogleSheetParser;

@Component
public class RegistrationDtoMapper {
    public AddParticipantDto mapRow(List<String> row,
                                        Map<String,Integer> colIndexMap,
                                        List<Integer> categoriesCols,
                                        List<String> genres){
        AddParticipantDto dto = new AddParticipantDto();

        Integer nameIdx = colIndexMap.get(SheetHeader.NAME);
        Integer emailIdx = colIndexMap.get(SheetHeader.EMAIL);

        if (nameIdx == null || emailIdx == null) return dto;
        if (row.size() <= nameIdx || row.size() <= emailIdx) return dto;

        dto.setParticipantName(row.get(nameIdx));
        dto.setParticipantEmail(row.get(emailIdx));

        if (colIndexMap.containsKey(SheetHeader.LOCAL_OVERSEAS)) {
            int residencyIdx = colIndexMap.get(SheetHeader.LOCAL_OVERSEAS);
            if (row.size() > residencyIdx) {
                dto.setResidency(row.get(residencyIdx));
            }
        }

        if (colIndexMap.containsKey(SheetHeader.PAYMENT_STATUS)) {
            int paymentIdx = colIndexMap.get(SheetHeader.PAYMENT_STATUS);
            dto.setPaymentStatus(row.size() > paymentIdx && Boolean.parseBoolean(row.get(paymentIdx)));
        } else {
            dto.setPaymentStatus(true);
        }

        if (colIndexMap.containsKey(SheetHeader.SCREENSHOT)) {
            int screenshotIdx = colIndexMap.get(SheetHeader.SCREENSHOT);
            if (row.size() > screenshotIdx) {
                dto.setScreenshotUrl(row.get(screenshotIdx));
            }
        }

        List<String> categories = new ArrayList<>();
        for (Integer i : categoriesCols){
            if (row.size() > i) {
                categories = GoogleSheetParser.normalizeGenre(row.get(i).toLowerCase(), genres);
                if(!categories.isEmpty()) break;
            }
        }
        dto.setGenres(categories);
        return dto;
    }
}
