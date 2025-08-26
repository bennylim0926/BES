package com.example.BES.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.BES.dtos.RegistrationDto;
import com.example.BES.enums.SheetHeader;
import com.example.BES.parsers.GoogleSheetParser;

@Component
public class RegistrationDtoMapper {
    public RegistrationDto mapRow(List<String> row, 
                                        Map<String,Integer> colIndexMap, 
                                        List<Integer> categoriesCols, 
                                        List<String> genres){
        RegistrationDto dto = new RegistrationDto();
        dto.setName(row.get(colIndexMap.get(SheetHeader.NAME)));
        dto.setEmail(row.get(colIndexMap.get(SheetHeader.EMAIL)));
        dto.setResidency(row.get(colIndexMap.get(SheetHeader.LOCAL_OVERSEAS)));
        dto.setPaymentStatus(Boolean.parseBoolean(row.get(colIndexMap.get(SheetHeader.PAYMENT_STATUS))));
        List<String> categories = new ArrayList<>();
        for (Integer i : categoriesCols){
            categories = GoogleSheetParser.normalizeGenre(row.get(i), genres);
            if(!categories.isEmpty()) break;
        }
        dto.setCategories(categories);
        return dto;                                   
    }
}
