package com.example.BES.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "feedback_tag")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FeedbackTag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String label;

    @ManyToOne
    @JoinColumn(name = "group_id", nullable = false)
    private FeedbackTagGroup group;
}
