package com.example.BES.mapper;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.example.BES.dtos.GetParticipantByEventDto;
import com.example.BES.models.Event;
import com.example.BES.models.EventGenre;
import com.example.BES.models.EventParticipant;

@Component
public class EventParticipantDtoMapper {
    public List<GetParticipantByEventDto> mapRow(List<EventParticipant> results){
        List<GetParticipantByEventDto> res = new ArrayList<>();
        for(EventParticipant eP : results){
            GetParticipantByEventDto dto = new GetParticipantByEventDto();
            dto.name = eP.getParticipant().getParticipantName();
            dto.residency = eP.getResidency();
            dto.genre = eP.getGenre();
            res.add(dto);
        }
        return res;
    }
}
