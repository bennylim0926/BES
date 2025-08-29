package com.example.BES.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AddParticipantDto {
    public String eventName;
    public String participantName;
    public String participantEmail;
}
