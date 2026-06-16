package com.example.BES.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "battle_active_category")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BattleActiveCategory {

    @Id
    private Integer id;

    @Column(name = "event_name")
    private String eventName;

    @Column(name = "category_name")
    private String categoryName;
}
