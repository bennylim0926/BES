package com.example.BES.dtos;

import java.util.ArrayList;
import java.util.List;

public class ImportResultDto {
    public int imported;
    public int existing;
    public int skipped;
    public List<DetailItem> errors   = new ArrayList<>();
    public List<DetailItem> warnings = new ArrayList<>();
    public List<DetailItem> info     = new ArrayList<>();

    public static class DetailItem {
        public int row;
        public String name;
        public String reason;
        public String severity;

        public DetailItem(int row, String name, String reason, String severity) {
            this.row = row;
            this.name = name;
            this.reason = reason;
            this.severity = severity;
        }
    }

    public void addError(int row, String name, String reason) {
        errors.add(new DetailItem(row, name, reason, "error"));
    }

    public void addWarning(int row, String name, String reason) {
        warnings.add(new DetailItem(row, name, reason, "warning"));
    }

    public void addInfo(int row, String name, String reason) {
        info.add(new DetailItem(row, name, reason, "info"));
    }
}
