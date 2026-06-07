package com.example.BES.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "battle_genre_state", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"event_name", "genre_name"})
})
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BattleGenreState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_name")
    private String eventName;

    @Column(name = "genre_name")
    private String genreName;

    @Column(name = "bracket_json", columnDefinition = "TEXT")
    private String bracketJson;

    @Column(name = "top_size")
    private Integer topSize;

    @Column(name = "current_round_index")
    private Integer currentRoundIndex;

    @Column(name = "current_pair_left")
    private String currentPairLeft;

    @Column(name = "current_pair_left_members", columnDefinition = "TEXT")
    private String currentPairLeftMembers;

    @Column(name = "current_pair_right")
    private String currentPairRight;

    @Column(name = "current_pair_right_members", columnDefinition = "TEXT")
    private String currentPairRightMembers;

    @Column(name = "is_final")
    private Boolean isFinal;

    @Column(name = "battle_phase")
    private String battlePhase;

    @Column(name = "judges_json", columnDefinition = "TEXT")
    private String judgesJson;

    @Column(name = "champion")
    private String champion;

    @Column(name = "smoke_list_json", columnDefinition = "TEXT")
    private String smokeListJson;

    @Column(name = "resolved_participants_json", columnDefinition = "TEXT")
    private String resolvedParticipantsJson;

    @Column(name = "format_timer_json", columnDefinition = "TEXT")
    private String formatTimerJson;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
