package com.example.BES.models;

import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Judge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long judgeId;
    private String name;

    @ManyToMany(mappedBy = "judges")
    private List<Event> events;
}
