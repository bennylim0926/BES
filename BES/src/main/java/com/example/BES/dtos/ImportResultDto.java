package com.example.BES.dtos;

import java.util.ArrayList;
import java.util.List;

public class ImportResultDto {
    public int imported;
    public int existing;
    public int skipped;
    public List<SkippedRow> errors = new ArrayList<>();

    public static class SkippedRow {
        public int row;
        public String name;
        public String reason;

        public SkippedRow(int row, String name, String reason) {
            this.row = row;
            this.name = name;
            this.reason = reason;
        }
    }
}
