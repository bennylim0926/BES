-- Performance indexes on foreign key columns and common filter columns

-- event_participant: look up all participants for an event
CREATE INDEX IF NOT EXISTS idx_event_participant_event_id ON event_participant(event_id);
CREATE INDEX IF NOT EXISTS idx_event_participant_participant_id ON event_participant(participant_id);

-- event_genre_participant: look up entries by event/genre, and by assigned judge
CREATE INDEX IF NOT EXISTS idx_egp_event_genre ON event_genre_participant(event_id, genre_id);
CREATE INDEX IF NOT EXISTS idx_egp_participant_id ON event_genre_participant(participant_id);
CREATE INDEX IF NOT EXISTS idx_egp_judge_id ON event_genre_participant(judge_id);

-- score: look up scores by event/genre/participant and by judge
CREATE INDEX IF NOT EXISTS idx_score_egp ON score(event_id, genre_id, participant_id);
CREATE INDEX IF NOT EXISTS idx_score_judge_id ON score(judge_id);

-- audition_feedback: look up feedback by participant/event/genre and by judge
CREATE INDEX IF NOT EXISTS idx_audition_feedback_egp ON audition_feedback(event_id, genre_id, participant_id);
CREATE INDEX IF NOT EXISTS idx_audition_feedback_judge_id ON audition_feedback(judge_id);

-- pickup_crew: look up crews for an event
CREATE INDEX IF NOT EXISTS idx_pickup_crew_event_id ON pickup_crew(event_id);

-- pickup_crew_member: look up members for a crew
CREATE INDEX IF NOT EXISTS idx_pickup_crew_member_crew_id ON pickup_crew_member(crew_id);
CREATE INDEX IF NOT EXISTS idx_pickup_crew_member_participant_id ON pickup_crew_member(participant_id);
