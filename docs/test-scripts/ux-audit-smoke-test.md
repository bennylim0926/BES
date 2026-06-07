# UX Audit Smoke Test — Issue #61

## Emcee
- [ ] Token redemption lands on EmceeSession with role guidance
- [ ] AuditionList: timer controls clear, participant progress visible
- [ ] Score: scoreboard readable
- [ ] BattleControl: Setup panel hidden
- [ ] BattleControl: LiveMatchPanel visible with genre switcher, round tabs, bracket viewer
- [ ] BattleTimer: start, countdown, reset all work
- [ ] BattleTimer: auto-unlocks at 10s → phase becomes VOTING
- [ ] Manual "Open Voting Now" button available alongside timer
- [ ] Phase actions (Get Score, Next Pair) work
- [ ] WebSocket disconnect: "Reconnecting..." indicator

## Judge
- [ ] Token redemption lands on JudgeSession with role guidance
- [ ] AuditionList: can score participants, confirmation visible
- [ ] Score submit error: error banner with retry
- [ ] BattleJudge: judge identity clear
- [ ] BattleJudge: phase transitions clear (LOCKED → waiting message)
- [ ] BattleJudge: two-tap voting self-explanatory
- [ ] Removed from battle: "Not Assigned" overlay

## Helper
- [ ] Token redemption lands on HelperSession with role guidance
- [ ] EventDetails: Event Day tab active by default
- [ ] Walk-in add: success confirmation
- [ ] Walk-in add fails (duplicate): error banner
- [ ] Check-in flow: participant list scannable

## Organiser
- [ ] Login lands on MainMenu
- [ ] Create event: flow discoverable
- [ ] Import participants: errors actionable
- [ ] Assign judges: flow clear
- [ ] Release results: button prominent
- [ ] BattleControl: Setup panel fully functional
- [ ] Seed bracket: methods explained
- [ ] Reset bracket: two-step confirm works

## Admin
- [ ] All Organiser checks pass
- [ ] AdminPage: genre CRUD, judge management, feedback tags, accent color picker work
