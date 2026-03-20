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
        dto.setParticipantName(row.get(colIndexMap.get(SheetHeader.NAME)));        
        dto.setParticipantEmail(row.get(colIndexMap.get(SheetHeader.EMAIL)));       
        if(colIndexMap.containsKey(SheetHeader.LOCAL_OVERSEAS)){
            dto.setResidency(row.get(colIndexMap.get(SheetHeader.LOCAL_OVERSEAS)));
        }        
        int paymentIdx = colIndexMap.get(SheetHeader.PAYMENT_STATUS);
        dto.setPaymentStatus(row.size() > paymentIdx && Boolean.parseBoolean(row.get(paymentIdx)));     
        List<String> categories = new ArrayList<>();
        for (Integer i : categoriesCols){
            categories = GoogleSheetParser.normalizeGenre(row.get(i).toLowerCase(), genres);
            if(!categories.isEmpty()) break;
        }       
        dto.setGenres(categories);       
        return dto;                                   
    }
}
