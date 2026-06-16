package com.example.BES.models;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "event_category")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "format")
    private String format;

    @Column(name = "round_label")
    private String roundLabel;

    @Column(name = "number_color")
    private String numberColor;

    @Column(name = "sheet_aliases")
    private String sheetAliases;

    @Column(name = "solo_allowed", nullable = false)
    private boolean soloAllowed = true;

    @ToString.Exclude
    @OneToMany(mappedBy = "eventCategory")
    private List<EventCategoryParticipant> participants;

    @ManyToMany
    @JoinTable(
        name = "event_category_judge",
        joinColumns = @JoinColumn(name = "event_category_id"),
        inverseJoinColumns = @JoinColumn(name = "judge_id")
    )
    private List<Judge> judges = new ArrayList<>();
}
