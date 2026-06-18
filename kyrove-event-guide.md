# Kyrove — Event Day Guide

Kyrove runs your audition event from start to finish. Think of it as four stages: **Setup → Registration → Auditions → Results**. Different people handle different parts.

---

## Staff Links — The Short Version

Staff links are **one-click access**. Instead of creating accounts and remembering passwords, each of your event-day staff gets a special link. They tap it and they're in — no login, no confusion.

### How They Work

You (the organiser) generate the links from the event page. Each link is locked to:
- A **specific event** — can't accidentally end up in the wrong event
- A **specific role** — the link knows whether you're an Emcee, Judge, or Helper

You copy the link and send it to your staff however you want — WhatsApp, email, Airdrop. They open it on their phone and they're taken straight to their screen.

### The Three Types

**Emcee link**
- One link covers the whole event
- The emcee picks which category they're running after they open it
- Can be shared among multiple emcees if you have different people running different rooms

**Judge links**
- One link **per judge**, per event
- Each link is tied to a specific judge — Judge A's link only lets them score as Judge A
- If a judge loses their link, you can revoke it and generate a new one
- Personal — don't share judge links between people

**Helper link**
- One link for the check-in desk
- Can be shared among the check-in team
- Gives access to check-in, walk-ins, and the audition display setup

### What to Tell Your Staff

> "I'm sending you a link. Open it on your phone. That's it — you don't need to create an account or remember a password. It'll take you straight to your screen."

That's the whole pitch. No usernames, no passwords, no app to install.

---

## Stage 1: Setup (Organiser)

This is where you build the event. You'll do this before event day.

### Importing Your Participant List

If you have a Google Sheet of registered participants, Kyrove can pull it in. If the sheet gets updated later, you can re-import — it won't duplicate people, it'll only pick up new rows. The rule is: make sure names are consistent between imports so the system can match them.

### Creating Categories

Categories are the competition formats — like "Popping 1v1", "Hip-Hop 2v2", "Breaking 1v1". You can add them manually or let Kyrove suggest them from your sheet data. Each category gets its own format and settings.

### Scoring — How It Works

You have two choices:
- **Single score** — each judge gives one overall number per participant
- **Custom criteria** — you define what judges score on (e.g., Musicality, Technique, Originality) and how much each one is worth. Judges score each aspect separately.

### Feedback — What It Is and Why Use It

Feedback lets judges give participants notes beyond just a number. You create groups of pre-set tags (e.g., "Musicality", "Foundations", "Performance") and judges pick tags that apply plus an optional written note. Think of it as structured commentary — faster than writing everything from scratch, but more personal than just a score.

Participants see their feedback when they scan their QR code at the end — but only if you choose to release it.

### Releasing Scores and Feedback

You control whether results are public. There's a toggle — when you turn it on, participants who scan their QR code will see their scores (and feedback, if you enabled it). You can wait until after the event to release, or keep it off entirely.

### Generating Session Links

From the event page, you generate links for your event-day staff. See the Staff Links section above for details on the three types.

---

## Stage 2: Registration / Check-In (Helper)

The Helper runs the check-in desk. This is the first thing participants see.

### Checking In Participants

When someone arrives, the Helper finds them in the list and checks them in. The system confirms their categories and gives them their audition number.

### About the QR Code

After check-in, the Helper shows the participant a QR code. This is how they'll look up their results later. It appears automatically — tell participants to take a photo of it with their phone.

If you've enabled feedback or score release, the participant will see everything when they scan it. If not, they'll see a message that results aren't available yet.

### Adding Walk-ins

If someone shows up who wasn't on the sheet, the Helper can add them on the spot — name, category, team members if it's a team format.

### Resolving Tie-breakers

If two participants have the same score, Kyrove flags it. The Helper (or Organiser) can break the tie by picking who advances. The system tracks these decisions so you don't lose them if the page refreshes.

---

## Stage 3: Auditions (Emcee + Judge + Helper)

This is the live part. Three roles working together.

---

### Emcee — Running the Flow

**Using the audition list**
The emcee picks a category and sees a "Now on Stage" card with the current participant's name and number. They swipe left for the next participant, right for the previous one. The queue at the top shows what's coming up.

**The timer**
A countdown timer sits at the bottom. The emcee starts it for each audition. Judges and the audience display see the same timer.

**How the emcee controls the audition display**
Whatever the emcee sees on the "Now on Stage" card — the participant name, the audition number, the timer — gets broadcast to the audition display screen. This is the screen the audience or stream sees (you set it up as an OBS browser source).

**Important:** If you're running multiple audition rooms at the same time (e.g., Popping in Room A, Breaking in Room B), each room needs its own emcee and its own display screen. You'll prepare one OBS source per room — just copy the display URL and change the category. Kyrove handles the rest.

**Setting up the audition display**
Each category gets its own display URL. Open the URL in OBS as a browser source. When the emcee starts the timer and advances rounds, the display updates live. You need one screen per category running simultaneously — so 5 categories running at once = 5 OBS sources to prepare.

---

### Judge — Scoring and Feedback

**How to give a score**
The judge sees one participant card at a time. They tap numbers on the keypad to enter a score, then submit. If the event uses custom criteria, they score each aspect separately — the system shows which criteria to score and the weight of each.

**How to give feedback**
If feedback is enabled, after submitting a score the judge sees the feedback panel. They can:
- Tap pre-set tags that apply (you set these up in Stage 1)
- Write an optional note

Tags are organised into groups — so a judge might pick "Good foundations" from the Technique group and "Needs variety" from the Musicality group. This is much faster than writing full comments for every participant.

---

### Helper — During Auditions

During auditions, the Helper keeps managing check-ins and can also monitor the audition display to make sure everything looks right on screen. They're the go-to for any participant questions about numbers or results.

---

## Stage 4: After the Event

**Results via QR**
When you're ready (you control the toggle), participants scan their QR code and see their scores and feedback. No app to install — it opens in their phone browser.

**What participants see**
- If you enabled both: score + feedback tags + any written notes from judges
- If you only enabled score release: just the score
- If neither: a "results not yet available" message

---

## Quick Reference: Who Does What

| Role | When | What |
|------|------|------|
| Organiser | Before event | Import sheet, create categories, set scoring mode, define feedback tags, generate staff links |
| Organiser | During/after | Decide when to release results to participants |
| Helper | Event day | Check in participants, show QR codes, add walk-ins, resolve ties, set up display screens |
| Emcee | During auditions | Control the flow — advance rounds, run timer, announce participants |
| Judge | During auditions | Score each participant, give feedback tags and notes |
