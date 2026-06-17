package com.example.BES.services;

import com.example.BES.models.AppConfig;
import com.example.BES.respositories.AppConfigRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

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

    private String generatePasscode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(chars.charAt(secureRandom.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
