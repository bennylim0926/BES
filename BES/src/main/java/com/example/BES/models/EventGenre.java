package com.example.BES.models;

import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventGenre {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne
    @JoinColumn(name = "genre_id", nullable = true)
    private Genre genre;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "format")
    private String format;

    @Column(name = "sheet_aliases")
    private String sheetAliases;

    @ToString.Exclude
    @OneToMany(mappedBy = "eventGenre")
    private List<EventGenreParticipant> participants;
}
