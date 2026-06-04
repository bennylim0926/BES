package com.example.BES.services;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
