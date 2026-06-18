package com.example.BES.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BES.dtos.AddParticipantDto;
import com.example.BES.dtos.AddWalkInDto;
import com.example.BES.models.Participant;
import com.example.BES.respositories.ParticipantRepo;

@Service
public class ParticipantService {
    @Autowired
    ParticipantRepo repo;

    public Participant addParticpantService(AddParticipantDto dto){
        String name = dto.getParticipantName().trim();
        return repo.findFirstByParticipantNameIgnoreCase(name).orElseGet(() -> {
            Participant participant = new Participant();
            participant.setParticipantName(name);
            return repo.saveAndFlush(participant);
        });
    }

    public Participant addWalkInService(AddWalkInDto dto){
        String name = dto.name.trim();
        Participant participant = repo.findFirstByParticipantNameIgnoreCase(name).orElse(new Participant());
        if(participant.getParticipantName() == null){;
            participant.setParticipantName(name);
            participant = repo.saveAndFlush(participant);
        }
        return participant;
    }
}   
