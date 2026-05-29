# BES — Battle Event System

Full-stack web app for managing dance battle events: registration, judging, scoring, real-time battle control, and results portal.

**Stack:** Vue 3 · Spring Boot · PostgreSQL · Nginx

---

## Running locally

### With Docker (recommended)
```bash
cp .env.example .env   # fill in required vars
docker-compose up --build --no-cache
```
- Frontend: `http://localhost`
- Backend: `http://localhost:5050`

### Without Docker
```bash
# Backend (from BES/)
mvn spring-boot:run

# Frontend (from BES-frontend/)
npm install && npm run dev   # http://localhost:5173
```

---

## Dev tooling — agent-dispatch (optional)

This repo includes [agent-dispatch](https://github.com/bennylim0926/agent-dispatch) as a git submodule at `.agent-dispatch/`. It dispatches implementation tasks to a DeepSeek container agent, keeping Claude tokens for planning and code review.

It is **optional personal dev tooling** — you do not need it to run or contribute to BES.

### Setup (if you want to use it)
```bash
git submodule update --init .agent-dispatch
.agent-dispatch/init.sh java
# edit .agent-dispatch/.env — add your DeepSeek API key and test command
```

### Usage
Write a plan with `superpowers:writing-plans`, then when asked which execution approach, say **"container dispatch"**.

See [agent-dispatch README](.agent-dispatch/README.md) for full details.
