package com.example.BES.parsers;

import java.util.List;
import com.google.api.services.sheets.v4.model.ValueRange;

public class GoogleSheetParser {
    public static List<String> normalizeGenre(String event, List<String> genres) {
        return genres.stream()
                     .filter(event::contains)
                     .toList();
    }

    public static boolean columnExists(List<String> headers, String keyword) {
        return headers.stream()
                      .anyMatch(h -> h != null && h.equalsIgnoreCase(keyword));
    }

    public static List<String> readHeaders(ValueRange firstRow) {
        return firstRow.getValues().get(0).stream()
                       .map(Object::toString)
                       .toList();
    }
}
