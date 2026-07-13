docker compose up -d --build
please verify that the apps are running
(if a change looks missing: hard-refresh browser first, check `docker ps` CREATED time; only rebuild the affected service with --no-cache if genuinely stale — see CLAUDE.md Docker rule)
