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
        Participant participant = new Participant();
        participant.setParticipantName(dto.getParticipantName());
        return repo.save(participant);
    }

    public Participant addWalkInService(AddWalkInDto dto){
        Participant participant = repo.findByParticipantName(dto.name).orElse(new Participant());
        if(participant.getParticipantName() == null){;
            participant.setParticipantName(dto.name);
            participant = repo.save(participant);
        }
        return participant;
    }
}   
