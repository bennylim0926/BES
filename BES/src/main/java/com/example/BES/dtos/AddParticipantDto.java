package com.example.BES.dtos;

import java.util.List;
import java.util.Map;

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
    public String residency;
    public List<String> genres;
    public Boolean paymentStatus;
    public String screenshotUrl;
    public String stageName;
    public String teamName;
    public List<String> memberNames;
    public Map<String, String> genreFormats;
}
