package com.example.BES.services;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BES.clients.GoogleSheetClient;
import com.example.BES.config.GoogleSheetConfig;
import com.example.BES.dtos.AddParticipantDto;
import com.example.BES.dtos.AddParticipantToEventDto;
import com.example.BES.dtos.GoogleSheetFileDto;
import com.example.BES.enums.Genre;
import com.example.BES.enums.SheetHeader;
import com.example.BES.mapper.RegistrationDtoMapper;
import com.example.BES.parsers.GoogleSheetParser;
import com.google.api.services.sheets.v4.model.BatchGetValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;

@Service
public class GoogleSheetService {
    private static final String CATEGORY_KEYWORD = "categor";
    private final static List<String> PAYMENT_KEYWORDS = new ArrayList<>(Arrays.asList(SheetHeader.EMAIL, SheetHeader.NAME, SheetHeader.PAYMENT_STATUS, SheetHeader.CATEGORIES, SheetHeader.LOCAL_OVERSEAS));
    private final static List<String> SCREENSHOT_KEYWORDS = new ArrayList<>(Arrays.asList("screenshot", "receipt", "proof", "prove", "payment"));

    @Autowired
    private GoogleSheetClient sheetClient;

    @Autowired
    private RegistrationDtoMapper mapper;

    @Autowired
    GoogleSheetConfig config;

    Map<String, BiConsumer<GoogleSheetFileDto, List<String>>> actions;
    List<String> genres;

    public GoogleSheetService() {
        genres = new ArrayList<>();
        actions = new HashMap<>();

        for (Genre g : Genre.values()) {
            for (String matchString : g.getAllMatchStrings()) {
                genres.add(matchString);
                final String key = matchString;
                switch (g) {
                    case POPPING   -> actions.put(key, (dto, list) -> dto.setPopping(categoriesCount(list, key)));
                    case WAACKING  -> actions.put(key, (dto, list) -> dto.setWaacking(categoriesCount(list, key)));
                    case LOCKING   -> actions.put(key, (dto, list) -> dto.setLocking(categoriesCount(list, key)));
                    case BREAKING  -> actions.put(key, (dto, list) -> dto.setBreaking(categoriesCount(list, key)));
                    case HIPHOP    -> actions.put(key, (dto, list) -> dto.setHiphop(categoriesCount(list, key)));
                    case OPEN      -> actions.put(key, (dto, list) -> dto.setOpen(categoriesCount(list, key)));
                    case AUDIENCE  -> actions.put(key, (dto, list) -> dto.setAudience(categoriesCount(list, key)));
                    case ROOKIE    -> actions.put(key, (dto, list) -> dto.setRookie(categoriesCount(list, key)));
                    case SMOKE     -> actions.put(key, (dto, list) -> dto.setSmoke(categoriesCount(list, key)));
                }
            }
        }
    }

    public GoogleSheetFileDto getParticipantsBreakDown(String fileId) throws IOException {
        GoogleSheetFileDto dto = new GoogleSheetFileDto();
        List<Integer> matchingCategoriesIndixes = getCategoriesColumns(fileId);

        if (matchingCategoriesIndixes.isEmpty()) {
            return dto;
        }

        List<String> matchingColumnAlphabet = new ArrayList<>();
        for (Integer index : matchingCategoriesIndixes) {
            String colLetter = colIndexToLetter(index + 1);
            matchingColumnAlphabet.add(colLetter + ":" + colLetter);
        }

        BatchGetValuesResponse response = sheetClient.batchGet(fileId, matchingColumnAlphabet);
        List<ValueRange> valueRanges = response.getValueRanges();

        int maxRows = 0;
        for (ValueRange vr : valueRanges) {
            List<List<Object>> vals = vr.getValues();
            if (vals != null && vals.size() > maxRows) {
                maxRows = vals.size();
            }
        }

        List<String> combined = new ArrayList<>();
        for (int i = 1; i < maxRows; i++) {
            String value = "";
            for (ValueRange vr : valueRanges) {
                List<List<Object>> vals = vr.getValues();
                if (vals != null && vals.size() > i) {
                    List<Object> cell = vals.get(i);
                    if (cell != null && !cell.isEmpty()) {
                        value = cell.get(0).toString();
                        break;
                    }
                }
            }
            combined.add(value);
        }

        setDtoCategory(dto, combined);
        return dto;
    }

    public void insertPaymentColumn(String fileId) throws IOException {
        ValueRange headerRange = sheetClient.getRange(fileId, "1:1");
        List<String> headers = GoogleSheetParser.readHeaders(headerRange);
        if (!GoogleSheetParser.columnExists(headers, SheetHeader.PAYMENT_STATUS)) {
            int rowSize = sheetClient.getSheetSize(fileId);
            sheetClient.insertPaymentCheckboxes(fileId, headers.size(), rowSize, sheetClient.getSheetId(fileId));
        }
    }

    public List<AddParticipantDto> getAllImportableParticipants(AddParticipantToEventDto dto)
            throws IOException {
        List<AddParticipantDto> importable = new ArrayList<>();
        List<String> originalHeaders = getHeaders(dto.fileId);
        Map<String, Integer> colIndexMap = getColumnIndexMap(dto.fileId, originalHeaders);
        List<List<String>> resultString = getsheetAllRows(dto.fileId, originalHeaders);
        List<Integer> categoriesColumn = getCategoriesColumns(dto.fileId);
        List<Integer> memberCols = getMemberNameColumns(originalHeaders);

        for (List<String> res : resultString) {
            AddParticipantDto participant = mapper.mapRow(res, colIndexMap, categoriesColumn, genres, memberCols);
            String name = participant.getParticipantName();
            String email = participant.getParticipantEmail();
            if (name != null && !name.isBlank() && email != null && !email.isBlank()) {
                importable.add(participant);
            }
        }
        return importable;
    }

    public Integer getSheetSizeService(String fileId) throws IOException {
        return sheetClient.getSheetSize(fileId) - 1;
    }

    /*
     * Helper functions region
     */
    private Map<String, Integer> getColumnIndexMap(String fileId, List<String> headers) throws IOException {
        Map<String, Integer> colIndexMap = new HashMap<>();

        // Pre-scan for stage name and team name before any header modification
        for (int i = 0; i < headers.size(); i++) {
            String h = headers.get(i).toLowerCase();
            if (h.contains(SheetHeader.STAGE_NAME)) colIndexMap.putIfAbsent(SheetHeader.STAGE_NAME, i);
            if (h.contains(SheetHeader.TEAM_NAME))  colIndexMap.putIfAbsent(SheetHeader.TEAM_NAME, i);
        }

        // Normalize headers: remove extra name columns (passing pre-scan map so special cols are skipped)
        List<String> normalizedHeaders = headers;
        if (getNameHeaderCount(headers) > 1) {
            normalizedHeaders = removeExtraName(headers, colIndexMap);
        }

        for (int i = 0; i < normalizedHeaders.size(); i++) {
            for (String keyword : PAYMENT_KEYWORDS) {
                if (normalizedHeaders.get(i).toLowerCase().contains(keyword.toLowerCase())) {
                    colIndexMap.putIfAbsent(keyword, i);
                }
            }
            String headerLower = normalizedHeaders.get(i).toLowerCase();
            for (String screenshotKw : SCREENSHOT_KEYWORDS) {
                if (headerLower.contains(screenshotKw) && !colIndexMap.containsKey(SheetHeader.SCREENSHOT)) {
                    colIndexMap.put(SheetHeader.SCREENSHOT, i);
                    break;
                }
            }
        }
        return colIndexMap;
    }

    private Integer getNameHeaderCount(List<String> headers) {
        int count = 0;
        for (String h : headers) {
            if (h.toLowerCase().contains("name")) {
                count++;
            }
        }
        return count;
    }

    /**
     * Normalizes headers by marking extra name columns as "ignore".
     * Stage name and team name columns (already pre-scanned) are marked as "ignore" so they
     * don't compete with the primary participant name column. Member name columns are also
     * marked as "ignore" since they are captured separately by getMemberNameColumns.
     * The first remaining "name" column is kept as the primary participant name (NAME key).
     */
    private List<String> removeExtraName(List<String> headers, Map<String, Integer> preScanMap) {
        List<String> mutableHeaders = new ArrayList<>(headers);

        // Collect indices already captured as special columns
        Set<Integer> specialIndices = new HashSet<>();
        if (preScanMap.containsKey(SheetHeader.STAGE_NAME)) specialIndices.add(preScanMap.get(SheetHeader.STAGE_NAME));
        if (preScanMap.containsKey(SheetHeader.TEAM_NAME))  specialIndices.add(preScanMap.get(SheetHeader.TEAM_NAME));

        // Mark special columns as "ignore"
        for (int idx : specialIndices) {
            mutableHeaders.set(idx, "ignore");
        }

        // Mark member name columns as "ignore" (captured by getMemberNameColumns)
        for (int i = 0; i < mutableHeaders.size(); i++) {
            if (specialIndices.contains(i)) continue;
            String lower = mutableHeaders.get(i).toLowerCase();
            if (lower.contains("member") && lower.contains("name")) {
                mutableHeaders.set(i, "ignore");
            }
        }

        // Keep first remaining "name" column as NAME, mark rest as "ignore"
        boolean foundFirstName = false;
        for (int i = 0; i < mutableHeaders.size(); i++) {
            String value = mutableHeaders.get(i).toLowerCase();
            if (value.equals("ignore")) continue;
            if (value.contains("name")) {
                if (!foundFirstName) {
                    foundFirstName = true;
                } else {
                    mutableHeaders.set(i, "ignore");
                }
            }
        }
        return mutableHeaders;
    }

    /**
     * Returns column indices where the header contains both "member" and "name"
     * (e.g. "Member 2 Name", "Member 3 Name").
     */
    private List<Integer> getMemberNameColumns(List<String> headers) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < headers.size(); i++) {
            String lower = headers.get(i).toLowerCase();
            if (lower.contains("member") && lower.contains("name")) {
                indices.add(i);
            }
        }
        return indices;
    }

    private List<String> getHeaders(String fileId) throws IOException {
        ValueRange headerRange = sheetClient.getRange(fileId, "1:1");
        return GoogleSheetParser.readHeaders(headerRange);
    }

    private List<List<String>> getsheetAllRows(String fileId, List<String> headers) throws IOException {
        String range = "A2:" + colIndexToLetter(headers.size());
        ValueRange results = sheetClient.getRange(fileId, range);
        List<List<Object>> values = results.getValues();
        if (values == null) {
            return new ArrayList<>();
        }
        return values.stream()
                .map(row -> row.stream()
                        .map(Object::toString)
                        .collect(Collectors.toList()))
                .collect(Collectors.toList());
    }

    private Integer categoriesCount(List<String> data, String category) {
        return (int) data.stream()
                .filter(s -> s.toLowerCase().contains(category.toLowerCase()))
                .count();
    }

    private List<Integer> getCategoriesColumns(String fileId) throws IOException {
        ValueRange headerRange = sheetClient.getRange(fileId, "1:1");
        List<String> headers = GoogleSheetParser.readHeaders(headerRange);
        List<Integer> matchingColumnIndices = new ArrayList<>();
        for (int i = 0; i < headers.size(); i++) {
            if (headers.get(i).toLowerCase().contains(CATEGORY_KEYWORD.toLowerCase())) {
                matchingColumnIndices.add(i);
            }
        }
        return matchingColumnIndices;
    }

    private void setDtoCategory(GoogleSheetFileDto dto, List<String> data) {
        Set<String> categories = new HashSet<>(data);
        for (String category : categories) {
            List<String> normalizeCategories = GoogleSheetParser.normalizeGenre(category, genres);
            for (String normalizeCategory : normalizeCategories) {
                actions.get(normalizeCategory).accept(dto, data);
            }
        }
    }

    private static String colIndexToLetter(int index) {
        StringBuilder result = new StringBuilder();
        while (index > 0) {
            int rem = (index - 1) % 26;
            result.insert(0, (char) ('A' + rem));
            index = (index - 1) / 26;
        }
        return result.toString();
    }
}
