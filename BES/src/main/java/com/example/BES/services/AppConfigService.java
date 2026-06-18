package com.example.BES.services;

import com.example.BES.models.AppConfig;
import com.example.BES.respositories.AppConfigRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class AppConfigService {

    private static final String ACCENT_KEY = "accentColor";
    private static final String ACCENT_DEFAULT = "#ffffff";
    private static final String DEMO_PASSCODE_KEY = "demo_passcode";
    private static final String DEMO_ENABLED_KEY = "demo_enabled";

    private final AppConfigRepository appConfigRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public AppConfigService(AppConfigRepository appConfigRepository) {
        this.appConfigRepository = appConfigRepository;
    }

    // ---- accentColor (existing) ----

    public String getAccentColor() {
        return get(ACCENT_KEY, ACCENT_DEFAULT);
    }

    public String saveAccentColor(String color) {
        set(ACCENT_KEY, color);
        return color;
    }

    // ---- generic key-value ----

    public String get(String key, String defaultVal) {
        return appConfigRepository.findByKey(key)
                .map(AppConfig::getValue)
                .orElse(defaultVal);
    }

    public void set(String key, String value) {
        AppConfig config = appConfigRepository.findByKey(key)
                .orElse(new AppConfig(null, key, value));
        config.setValue(value);
        appConfigRepository.save(config);
    }

    // ---- demo ----

    public String getDemoPasscode() {
        String existing = get(DEMO_PASSCODE_KEY, null);
        if (existing == null || "CHANGEME".equals(existing)) {
            String generated = generatePasscode();
            set(DEMO_PASSCODE_KEY, generated);
            return generated;
        }
        return existing;
    }

    public void setDemoPasscode(String passcode) {
        set(DEMO_PASSCODE_KEY, passcode);
    }

    public boolean isDemoEnabled() {
        return "true".equals(get(DEMO_ENABLED_KEY, "true"));
    }

    public void setDemoEnabled(boolean enabled) {
        set(DEMO_ENABLED_KEY, Boolean.toString(enabled));
    }

    // ---- sheet column-header config ----

    public List<String> getSheetNameKeyword() {
        return parseCsv(get("sheet.nameKeyword", "name"));
    }

    public List<String> getSheetStageNameKeyword() {
        return parseCsv(get("sheet.stageNameKeyword", "stage name"));
    }

    public List<String> getSheetTeamNameKeywords() {
        return parseCsv(get("sheet.teamNameKeywords", "team,duo,battler,crew,group"));
    }

    public List<String> getSheetMemberNameKeywords() {
        return parseCsv(get("sheet.memberNameKeywords", "member,dancer"));
    }

    public List<String> getSheetCategoryKeywords() {
        return parseCsv(get("sheet.categoryKeywords", "categor"));
    }

    public List<String> getSheetEntryTypeKeyword() {
        return parseCsv(get("sheet.entryTypeKeyword", "entry type"));
    }

    public List<String> getSheetEmailKeyword() {
        return parseCsv(get("sheet.emailKeyword", "email"));
    }

    public List<String> getSheetPaymentKeyword() {
        return parseCsv(get("sheet.paymentKeyword", "payment status"));
    }

    public List<String> getSheetScreenshotKeywords() {
        return parseCsv(get("sheet.screenshotKeywords", "screenshot,receipt,proof,prove,payment"));
    }

    /**
     * Bulk-saves sheet config keys from the admin panel.
     */
    public void saveSheetConfig(Map<String, String> config) {
        config.forEach(this::set);
    }

    private List<String> parseCsv(String csv) {
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    private String generatePasscode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(chars.charAt(secureRandom.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
