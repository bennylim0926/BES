package com.example.BES.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventGenreBattleGuest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne
    @JoinColumn(name = "event_genre_id", nullable = false)
    private EventGenre eventGenre;

    @Column(name = "guest_name", nullable = false)
    private String guestName;

    @Column(name = "entry_round", nullable = false)
    private String entryRound;

    @Column(name = "member_names", columnDefinition = "TEXT")
    private String memberNamesRaw;

    public List<String> getMemberNames() {
        if (memberNamesRaw == null || memberNamesRaw.isBlank()) return Collections.emptyList();
        return Arrays.stream(memberNamesRaw.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }

    public void setMemberNames(List<String> members) {
        this.memberNamesRaw = (members == null || members.isEmpty())
            ? null
            : members.stream().filter(s -> s != null && !s.isBlank()).collect(Collectors.joining(","));
    }
}
