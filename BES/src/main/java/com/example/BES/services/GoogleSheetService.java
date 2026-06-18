package com.example.BES.services;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.example.BES.clients.GoogleSheetClient;
import com.example.BES.config.GoogleSheetConfig;
import com.example.BES.dtos.AddParticipantDto;
import com.example.BES.dtos.AddParticipantToEventDto;
import com.example.BES.enums.SheetHeader;
import com.example.BES.mapper.RegistrationDtoMapper;
import com.example.BES.models.EventCategory;
import com.example.BES.parsers.GoogleSheetParser;
import com.example.BES.respositories.EventCategoryRepo;
import com.example.BES.respositories.EventRepo;
import com.google.api.services.sheets.v4.model.BatchGetValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;

@Service
@Profile("!test")
public class GoogleSheetService {

    @Autowired
    private GoogleSheetClient sheetClient;

    @Autowired
    private RegistrationDtoMapper mapper;

    @Autowired
    GoogleSheetConfig config;

    @Autowired
    private EventRepo eventRepo;

    @Autowired
    private EventCategoryRepo eventCategoryRepo;

    @Autowired
    private AppConfigService appConfigService;

    public Map<String, Integer> getParticipantsBreakDown(String fileId) throws IOException {
        List<Integer> matchingCategoriesIndixes = getCategoriesColumns(fileId);

        if (matchingCategoriesIndixes.isEmpty()) {
            return new LinkedHashMap<>();
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

        return buildCategoryCounts(combined);
    }

    public void insertPaymentColumn(String fileId) throws IOException {
        ValueRange headerRange = sheetClient.getRange(fileId, "1:1");
        List<String> headers = GoogleSheetParser.readHeaders(headerRange);
        List<String> paymentKws = appConfigService.getSheetPaymentKeyword();
        boolean found = false;
        for (String h : headers) {
            if (containsAny(h.toLowerCase(), paymentKws)) { found = true; break; }
        }
        if (!found) {
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

        List<String> genreMatchStrings = loadCategoryMatchStrings(dto.eventName);
        for (List<String> res : resultString) {
            AddParticipantDto participant = mapper.mapRow(res, colIndexMap, categoriesColumn, genreMatchStrings, memberCols);
            String name = participant.getParticipantName();
            if (name != null && !name.isBlank()) {
                importable.add(participant);
            }
        }
        return importable;
    }

    public List<String> getAllCategoryValues(String fileId) throws IOException {
        List<Integer> categoryIndices = getCategoriesColumns(fileId);
        if (categoryIndices.isEmpty()) return new ArrayList<>();

        List<String> ranges = new ArrayList<>();
        for (int index : categoryIndices) {
            String colLetter = colIndexToLetter(index + 1);
            ranges.add(colLetter + ":" + colLetter);
        }

        BatchGetValuesResponse response = sheetClient.batchGet(fileId, ranges);
        List<ValueRange> valueRanges = response.getValueRanges();

        List<String> values = new ArrayList<>();
        for (ValueRange vr : valueRanges) {
            List<List<Object>> rows = vr.getValues();
            if (rows == null || rows.isEmpty()) continue;
            for (int i = 1; i < rows.size(); i++) {
                List<Object> cell = rows.get(i);
                if (cell != null && !cell.isEmpty() && cell.get(0) != null) {
                    String raw = cell.get(0).toString().trim();
                    if (raw.isBlank()) continue;
                    for (String part : raw.split(",")) {
                        String val = part.trim();
                        if (!val.isBlank()) values.add(val);
                    }
                }
            }
        }
        return values;
    }

    public Integer getSheetSizeService(String fileId) throws IOException {
        return sheetClient.getSheetSize(fileId) - 1;
    }

    /*
     * Helper functions region
     */
    private Map<String, Integer> getColumnIndexMap(String fileId, List<String> headers) throws IOException {
        Map<String, Integer> colIndexMap = new HashMap<>();

        List<String> stageNameKw = appConfigService.getSheetStageNameKeyword();
        List<String> teamNameKws = appConfigService.getSheetTeamNameKeywords();
        List<String> entryTypeKw = appConfigService.getSheetEntryTypeKeyword();

        // Pre-scan for stage name, team name, and entry type before any header modification
        for (int i = 0; i < headers.size(); i++) {
            String h = headers.get(i).toLowerCase();
            for (String kw : stageNameKw) {
                if (h.contains(kw)) { colIndexMap.putIfAbsent(SheetHeader.STAGE_NAME, i); break; }
            }
            for (String kw : teamNameKws) {
                if (h.contains(kw) && h.contains(appConfigService.getSheetNameKeyword().get(0))) {
                    colIndexMap.putIfAbsent(SheetHeader.TEAM_NAME, i); break;
                }
            }
            if (isTeamNameHeader(h)) colIndexMap.putIfAbsent(SheetHeader.TEAM_NAME, i);
            for (String kw : entryTypeKw) {
                if (h.contains(kw)) { colIndexMap.putIfAbsent(SheetHeader.ENTRY_TYPE, i); break; }
            }
        }

        // Normalize headers: remove extra name columns (passing pre-scan map so special cols are skipped)
        List<String> normalizedHeaders = headers;
        if (getNameHeaderCount(headers) > 1) {
            normalizedHeaders = removeExtraName(headers, colIndexMap);
        }

        // Build dynamic keyword lists from config
        List<String> paymentKeywords = new ArrayList<>();
        paymentKeywords.addAll(appConfigService.getSheetEmailKeyword());
        paymentKeywords.addAll(appConfigService.getSheetNameKeyword());
        paymentKeywords.addAll(appConfigService.getSheetPaymentKeyword());
        paymentKeywords.addAll(appConfigService.getSheetCategoryKeywords());

        List<String> screenshotKeywords = appConfigService.getSheetScreenshotKeywords();

        for (int i = 0; i < normalizedHeaders.size(); i++) {
            for (String keyword : paymentKeywords) {
                if (normalizedHeaders.get(i).toLowerCase().contains(keyword.toLowerCase())) {
                    colIndexMap.putIfAbsent(keyword, i);
                }
            }
            String headerLower = normalizedHeaders.get(i).toLowerCase();
            for (String screenshotKw : screenshotKeywords) {
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
        List<String> nameKws = appConfigService.getSheetNameKeyword();
        for (String h : headers) {
            String lower = h.toLowerCase();
            for (String kw : nameKws) {
                if (lower.contains(kw)) { count++; break; }
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

        List<String> memberKws = appConfigService.getSheetMemberNameKeywords();
        List<String> nameKws = appConfigService.getSheetNameKeyword();

        // Mark secondary member/dancer name columns as "ignore" (captured by getMemberNameColumns).
        // Primary member/dancer (number 1 or no number) stays as a candidate for primary NAME.
        for (int i = 0; i < mutableHeaders.size(); i++) {
            if (specialIndices.contains(i)) continue;
            String lower = mutableHeaders.get(i).toLowerCase();
            if (containsAny(lower, memberKws) && containsAny(lower, nameKws)) {
                if (isSecondaryMemberColumn(lower)) {
                    mutableHeaders.set(i, "ignore");
                }
            }
        }

        // Keep first remaining name column as NAME, mark rest as "ignore"
        boolean foundFirstName = false;
        for (int i = 0; i < mutableHeaders.size(); i++) {
            String value = mutableHeaders.get(i).toLowerCase();
            if (value.equals("ignore")) continue;
            if (containsAny(value, nameKws)) {
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
     * Returns column indices where the header indicates an additional team member
     * (e.g. "Member 2 Name", "Dancer 3 Name"). The primary member/dancer (number 1
     * or no number) is excluded — it serves as the main participant NAME instead.
     */
    private List<Integer> getMemberNameColumns(List<String> headers) {
        List<Integer> indices = new ArrayList<>();
        List<String> memberKws = appConfigService.getSheetMemberNameKeywords();
        List<String> nameKws = appConfigService.getSheetNameKeyword();
        for (int i = 0; i < headers.size(); i++) {
            String lower = headers.get(i).toLowerCase();
            if (containsAny(lower, memberKws) && containsAny(lower, nameKws)) {
                if (isSecondaryMemberColumn(lower)) {
                    indices.add(i);
                }
            }
        }
        return indices;
    }

    /**
     * Returns true when the header looks like a team/group/crew name rather than
     * an individual participant name. Matches headers that contain a name keyword plus a
     * team-indicator word and do NOT contain individual-level member keywords.
     */
    private boolean isTeamNameHeader(String header) {
        String h = header.toLowerCase();
        List<String> nameKws = appConfigService.getSheetNameKeyword();
        List<String> memberKws = appConfigService.getSheetMemberNameKeywords();
        List<String> teamKws = appConfigService.getSheetTeamNameKeywords();

        if (!containsAny(h, nameKws)) return false;
        if (containsAny(h, memberKws)) return false;
        return containsAny(h, teamKws);
    }

    /**
     * Returns true when a member/dancer name header contains a number greater
     * than 1 (e.g. "Dancer 2 Name", "Member 3 Name"). Headers with no number or
     * only the number 1 are treated as the primary participant name.
     */
    private boolean isSecondaryMemberColumn(String header) {
        Matcher m = Pattern.compile("\\b(\\d+)\\b").matcher(header);
        while (m.find()) {
            if (Integer.parseInt(m.group(1)) > 1) return true;
        }
        return false;
    }

    /** Returns true if {@code text} contains any of the given keywords (case-insensitive). */
    private static boolean containsAny(String text, List<String> keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
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

    private List<String> loadCategoryMatchStrings(String eventName) {
        List<String> all = new ArrayList<>();
        var event = eventRepo.findByEventNameIgnoreCase(eventName).orElse(null);
        if (event != null) {
            for (EventCategory eg : eventCategoryRepo.findByEvent(event)) {
                all.add(eg.getName().toLowerCase());
                if (eg.getSheetAliases() != null) {
                    for (String alias : eg.getSheetAliases().split(",")) {
                        String trimmed = alias.trim().toLowerCase();
                        if (!trimmed.isBlank()) all.add(trimmed);
                    }
                }
            }
        }
        return all;
    }

    private Map<String, Integer> buildCategoryCounts(List<String> data) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String value : data) {
            if (value == null || value.isBlank()) continue;
            String key = value.trim();
            counts.merge(key, 1, Integer::sum);
        }
        return counts;
    }

    private List<Integer> getCategoriesColumns(String fileId) throws IOException {
        ValueRange headerRange = sheetClient.getRange(fileId, "1:1");
        List<String> headers = GoogleSheetParser.readHeaders(headerRange);
        List<Integer> matchingColumnIndices = new ArrayList<>();
        List<String> categoryKws = appConfigService.getSheetCategoryKeywords();
        for (int i = 0; i < headers.size(); i++) {
            if (containsAny(headers.get(i).toLowerCase(), categoryKws)) {
                matchingColumnIndices.add(i);
            }
        }
        return matchingColumnIndices;
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
