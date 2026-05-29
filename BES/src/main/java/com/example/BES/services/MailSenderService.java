package com.example.BES.services;

import java.util.regex.Pattern;

public class MailSenderService {
    public static String normalizeKey(String key) {
        if (key == null) {
            return null;
        }
        return key.replaceAll("[ .]", "_");
    }
}
