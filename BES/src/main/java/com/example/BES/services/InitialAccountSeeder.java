package com.example.BES.services;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.example.BES.models.Account;
import com.example.BES.respositories.AccountRepository;

@Component
public class InitialAccountSeeder {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${BES_ADMIN_PASSWORD}")
    private String adminPassword;

    @Value("${BES_ORGANISER_PASSWORD}")
    private String organiserPassword;

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        if (accountRepository.findByUsername("admin").isEmpty()) {
            Account admin = new Account();
            admin.setUsername("admin");
            admin.setPasswordHash(passwordEncoder.encode(adminPassword));
            admin.setRole("ADMIN");
            admin.setReferralCode(generateReferralCode());
            admin.setCreatedAt(LocalDateTime.now());
            accountRepository.save(admin);
        }

        if (accountRepository.findByUsername("organiser").isEmpty()) {
            Account organiser = new Account();
            organiser.setUsername("organiser");
            organiser.setPasswordHash(passwordEncoder.encode(organiserPassword));
            organiser.setRole("ORGANISER");
            organiser.setReferralCode(generateReferralCode());
            organiser.setCreatedAt(LocalDateTime.now());
            accountRepository.save(organiser);
        }
    }

    private String generateReferralCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
