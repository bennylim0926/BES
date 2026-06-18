-- V48: Revoke duplicate session tokens
--
-- Historical bug: SessionTokenService.generateToken always created a new row
-- without revoking an existing matching token, and EventDetails.vue had an
-- auto-repair path that could fire concurrently. The result was multiple
-- active JUDGE tokens for the same (event, judge), plus occasionally
-- multiple EMCEE/HELPER tokens per event.
--
-- Going forward generateToken revokes existing matches first. This migration
-- cleans up the historical duplicates: for each (event, role, judge)
-- group with more than one active token, keep the most recently created
-- one and mark the rest revoked.

WITH ranked AS (
    SELECT token_id,
           ROW_NUMBER() OVER (
               PARTITION BY event_id, role, judge_id
               ORDER BY created_at DESC, token_id DESC
           ) AS rn
    FROM   session_token
    WHERE  revoked = FALSE
)
UPDATE session_token
SET    revoked = TRUE
WHERE  token_id IN (SELECT token_id FROM ranked WHERE rn > 1);
