package com.example.BES.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "battle_active_genre")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BattleActiveGenre {

    @Id
    private Integer id;

    @Column(name = "event_name")
    private String eventName;

    @Column(name = "genre_name")
    private String genreName;
}
