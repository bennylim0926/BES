package com.example.BES.services;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.BES.dtos.admin.GetOrganiserDto;
import com.example.BES.models.Account;
import com.example.BES.models.Event;
import com.example.BES.respositories.AccountRepository;
import com.example.BES.respositories.EventRepo;

@Service
public class AccountService {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private EventRepo eventRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<GetOrganiserDto> getAllOrganisers() {
        return accountRepository.findByRole("ORGANISER")
            .stream()
            .map(a -> new GetOrganiserDto(
                a.getAccountId(),
                a.getUsername(),
                a.getAssignedEvents() != null
                    ? a.getAssignedEvents().stream().map(Event::getEventId).collect(Collectors.toList())
                    : List.of()
            ))
            .collect(Collectors.toList());
    }

    @Transactional
    public void assignEvent(Long accountId, Long eventId) {
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new RuntimeException("Account not found: " + accountId));
        Event event = eventRepo.findById(eventId)
            .orElseThrow(() -> new RuntimeException("Event not found: " + eventId));

        if (account.getAssignedEvents() == null || !account.getAssignedEvents().contains(event)) {
            account.getAssignedEvents().add(event);
            accountRepository.save(account);
        }
    }

    @Transactional
    public void removeEvent(Long accountId, Long eventId) {
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new RuntimeException("Account not found: " + accountId));
        Event event = eventRepo.findById(eventId)
            .orElseThrow(() -> new RuntimeException("Event not found: " + eventId));

        if (account.getAssignedEvents() != null) {
            account.getAssignedEvents().remove(event);
            accountRepository.save(account);
        }
    }

    public Account createOrganiser(String username, String password) {
        if (accountRepository.findByUsername(username).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username already exists");
        }
        Account account = new Account();
        account.setUsername(username);
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setRole("ORGANISER");
        account.setReferralCode(generateReferralCode());
        account.setCreatedAt(LocalDateTime.now());
        return accountRepository.save(account);
    }

    @Transactional
    public void deleteOrganiser(Long accountId) {
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organiser not found"));
        accountRepository.delete(account);
    }

    private String generateReferralCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
