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
                                        List<String> genres,
                                        List<Integer> memberCols) {
        AddParticipantDto dto = new AddParticipantDto();

        Integer nameIdx = colIndexMap.get(SheetHeader.NAME);
        Integer emailIdx = colIndexMap.get(SheetHeader.EMAIL);
        Integer stageNameIdx = colIndexMap.get(SheetHeader.STAGE_NAME);
        Integer teamNameIdx = colIndexMap.get(SheetHeader.TEAM_NAME);

        // Fallback chain: Name → Stage Name → Team Name → skip row
        if (nameIdx == null) {
            if (stageNameIdx != null) {
                nameIdx = stageNameIdx;
            } else if (teamNameIdx != null) {
                nameIdx = teamNameIdx;
            } else {
                return dto; // no identity column — skip row
            }
        }

        if (row.size() <= nameIdx) return dto;

        dto.setParticipantName(row.get(nameIdx));

        // Stage name: dedicated column if present and different from identity, otherwise identity doubles as stage name
        if (stageNameIdx != null && !stageNameIdx.equals(nameIdx)
                && row.size() > stageNameIdx && !row.get(stageNameIdx).isBlank()) {
            dto.setStageName(row.get(stageNameIdx));
        } else {
            dto.setStageName(row.get(nameIdx));
        }

        // Team name
        if (teamNameIdx != null && row.size() > teamNameIdx && !row.get(teamNameIdx).isBlank()) {
            dto.setTeamName(row.get(teamNameIdx));
        }

        // Member names (additional team members beyond the leader)
        List<String> memberNames = new ArrayList<>();
        for (Integer idx : memberCols) {
            if (row.size() > idx && !row.get(idx).isBlank()) {
                memberNames.add(row.get(idx));
            }
        }
        dto.setMemberNames(memberNames);

        // Entry type: explicit column takes precedence; otherwise infer from team name presence
        Integer entryTypeIdx = colIndexMap.get(SheetHeader.ENTRY_TYPE);
        if (entryTypeIdx != null && row.size() > entryTypeIdx && !row.get(entryTypeIdx).isBlank()) {
            dto.setEntryType(row.get(entryTypeIdx).trim().toLowerCase());
        } else if (dto.getTeamName() != null && !dto.getTeamName().isBlank()) {
            dto.setEntryType("team");
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

        Map<String, String> categoryFormats = new java.util.HashMap<>();
        for (Integer i : categoriesCols) {
            if (row.size() > i && !row.get(i).isBlank()) {
                categoryFormats = GoogleSheetParser.parseCategoryFormats(row.get(i), genres);
                if (!categoryFormats.isEmpty()) break;
            }
        }
        dto.setCategoryFormats(categoryFormats);
        dto.setCategories(new ArrayList<>(categoryFormats.keySet()));

        return dto;
    }
}
