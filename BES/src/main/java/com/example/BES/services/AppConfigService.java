package com.example.BES.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.example.BES.models.AppConfig;
import com.example.BES.respositories.AppConfigRepository;

@Service
public class AppConfigService {

    private static final String ACCENT_KEY = "accentColor";
    private static final String ACCENT_DEFAULT = "#ffffff";

    @Autowired
    private AppConfigRepository repo;

    public String getAccentColor() {
        return repo.findByKey(ACCENT_KEY)
                   .map(AppConfig::getValue)
                   .orElse(ACCENT_DEFAULT);
    }

    public String saveAccentColor(String color) {
        AppConfig cfg = repo.findByKey(ACCENT_KEY)
                            .orElse(new AppConfig(null, ACCENT_KEY, ACCENT_DEFAULT));
        cfg.setValue(color);
        repo.save(cfg);
        return color;
    }
}
