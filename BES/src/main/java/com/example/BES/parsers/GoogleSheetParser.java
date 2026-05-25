package com.example.BES.parsers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.api.services.sheets.v4.model.ValueRange;

public class GoogleSheetParser {
    private static final Pattern FORMAT_PATTERN = Pattern.compile("\\d+v\\d+");

    public static List<String> normalizeGenre(String event, List<String> genres) {
        String eventLower = event.toLowerCase();
        return genres.stream()
                     .filter(eventLower::contains)
                     .toList();
    }

    public static boolean columnExists(List<String> headers, String keyword) {
        return headers.stream()
                      .anyMatch(h -> h != null && h.toLowerCase().contains(keyword.toLowerCase()));
    }

    public static List<String> readHeaders(ValueRange firstRow) {
        return firstRow.getValues().get(0).stream()
                       .map(Object::toString)
                       .toList();
    }

    /**
     * Parses a raw category cell (e.g. "Popping 1v1, Open Styles 3v3") into a map of
     * genre match-string → format string (e.g. "1v1", "3v3"). Format is null if not present.
     *
     * @param rawCellValue the raw comma-separated category string from the sheet
     * @param genres       all known genre match strings (labels + aliases)
     * @return map of genre label → format
     */
    public static Map<String, String> parseGenreFormats(String rawCellValue, List<String> genres) {
        Map<String, String> result = new HashMap<>();
        if (rawCellValue == null || rawCellValue.isBlank()) return result;

        String[] segments = rawCellValue.split(",");
        for (String segment : segments) {
            String seg = segment.toLowerCase().trim();
            if (seg.isBlank()) continue;

            // Extract format (e.g. "3v3") from segment
            Matcher m = FORMAT_PATTERN.matcher(seg);
            String format = m.find() ? m.group() : null;

            // Match against all known genre strings
            for (String genre : genres) {
                if (seg.contains(genre)) {
                    result.putIfAbsent(genre, format);
                }
            }
        }
        return result;
    }
}
