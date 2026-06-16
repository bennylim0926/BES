package com.example.BES.services;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class EventCategoryParticipantServiceValidationTest {

    private final EventCategoryParticipantService service = new EventCategoryParticipantService();

    @Test
    void parseFormatSize_returns_correct_size() {
        assertThat(service.parseFormatSize("1v1")).isEqualTo(1);
        assertThat(service.parseFormatSize("2v2")).isEqualTo(2);
        assertThat(service.parseFormatSize("3v3")).isEqualTo(3);
        assertThat(service.parseFormatSize("4v4")).isEqualTo(4);
    }

    @Test
    void parseFormatSize_returns_zero_for_null() {
        assertThat(service.parseFormatSize(null)).isEqualTo(0);
    }

    @Test
    void parseFormatSize_returns_zero_for_invalid() {
        assertThat(service.parseFormatSize("abc")).isEqualTo(0);
    }

    @Test
    void validateTeamEntry_throws_when_teamName_blank() {
        assertThatThrownBy(() -> service.validateTeamEntry("2v2", "", List.of("Member A")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Team name is required");
    }

    @Test
    void validateTeamEntry_throws_when_teamName_null() {
        assertThatThrownBy(() -> service.validateTeamEntry("2v2", null, List.of("Member A")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Team name is required");
    }

    @Test
    void validateTeamEntry_throws_when_member_count_wrong() {
        assertThatThrownBy(() -> service.validateTeamEntry("2v2", "Team A", List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("additional member");
    }

    @Test
    void validateTeamEntry_passes_valid_2v2() {
        assertThatCode(() -> service.validateTeamEntry("2v2", "Team A", List.of("Member A")))
            .doesNotThrowAnyException();
    }

    @Test
    void validateTeamEntry_passes_valid_3v3() {
        assertThatCode(() -> service.validateTeamEntry("3v3", "Crew X", List.of("B", "C")))
            .doesNotThrowAnyException();
    }

    @Test
    void validateTeamEntry_passes_valid_4v4() {
        assertThatCode(() -> service.validateTeamEntry("4v4", "Squad", List.of("M1", "M2", "M3")))
            .doesNotThrowAnyException();
    }

    @Test
    void validateTeamEntry_ignores_blank_members_in_count() {
        assertThatThrownBy(() -> service.validateTeamEntry("2v2", "Team A", Arrays.asList("", null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("additional member");
    }
}
