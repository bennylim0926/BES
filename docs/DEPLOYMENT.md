# Kyrove Deployment Guide — Hetzner + HTTPS

This is the step-by-step runbook for deploying Kyrove to a public server with a custom domain and HTTPS.

**Stack:** Hetzner CX22 (Ubuntu 24.04) · Namecheap domain · Let's Encrypt TLS · Docker Compose · Nginx (in the frontend container)

**Cost:** ~$6/mo server + ~$10/yr domain. No CI/CD in this phase — deploys are `git pull && docker compose up -d --build` on the server.

Placeholders used throughout — replace before running:
- `<SERVER_IP>` — your Hetzner server's IPv4
- `<DOMAIN>` — your registered domain, e.g. `kyrove.xyz`
- `<EMAIL>` — your email (for Let's Encrypt + Hetzner)

---

## Phase 0 — Prerequisites (~20 min, can run in parallel)

### 0a. Buy a domain on Namecheap

1. namecheap.com → search a name (`.xyz` ~$1 first year, `.com` ~$10/yr)
2. Disable upsells at checkout. Keep WhoisGuard (free).
3. After purchase: **Domain List → Manage** → confirm nameservers say "Namecheap BasicDNS".

### 0b. Create a Hetzner account

1. https://accounts.hetzner.com/signUp
2. Use a real name matching your card.
3. **Heads up:** Hetzner may email you within an hour asking for ID verification (passport/IC). Reply promptly. This is the #1 timing blocker — start it first.

### 0c. Generate an SSH key on your Mac (if you don't already have one)

```bash
ls ~/.ssh/id_ed25519.pub 2>/dev/null || ssh-keygen -t ed25519 -C "kyrove-deploy" -f ~/.ssh/id_ed25519
cat ~/.ssh/id_ed25519.pub   # copy this for the next step
```

---

## Phase 1 — Provision the server (~10 min)

Hetzner Cloud Console → **New Project** → name it `kyrove`.

**Add Server:**
- Location: **Singapore** (or closest to your users)
- Image: **Ubuntu 24.04**
- Type: **CX22** (€4.59/mo · 2 vCPU · 4 GB RAM · 40 GB SSD)
- Networking: **Public IPv4** ✓
- SSH Keys: paste `id_ed25519.pub`
- Name: `kyrove-prod`
- **Enable Backups** (+20% cost, ~$1/mo — recommended)

Copy the **IPv4 address** that appears → this is `<SERVER_IP>`.

Smoke-test from your Mac:
```bash
ssh root@<SERVER_IP>
```

---

## Phase 2 — Point DNS at the server (~5 min + ~15 min wait)

Namecheap → **Domain List → Manage → Advanced DNS**. Delete any default parking records. Add:

| Type | Host | Value         | TTL       |
|------|------|---------------|-----------|
| A    | `@`  | `<SERVER_IP>` | Automatic |
| A    | `www`| `<SERVER_IP>` | Automatic |

Wait ~15 min, then verify from your Mac:
```bash
dig +short <DOMAIN>
dig +short www.<DOMAIN>
```
Both must print `<SERVER_IP>` before you proceed — Let's Encrypt will fail otherwise.

---

## Phase 3 — Harden the server (~15 min)

SSH in as root: `ssh root@<SERVER_IP>`

```bash
# 3.1 — create deploy user
adduser deploy            # set a strong password when prompted
usermod -aG sudo deploy

# 3.2 — copy SSH key
rsync --archive --chown=deploy:deploy ~/.ssh /home/deploy
```

**Open a SECOND terminal on your Mac** and verify `ssh deploy@<SERVER_IP>` works **before** doing 3.3 — if you lock root out without confirming deploy works, you're locked out entirely.

```bash
# 3.3 — disable root SSH + password auth (only after deploy login confirmed)
sed -i 's/^#*PermitRootLogin.*/PermitRootLogin no/' /etc/ssh/sshd_config
sed -i 's/^#*PasswordAuthentication.*/PasswordAuthentication no/' /etc/ssh/sshd_config
systemctl restart ssh

# 3.4 — firewall
ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable
ufw status
```

From now on always log in as `deploy`: `ssh deploy@<SERVER_IP>`.

---

## Phase 4 — Install Docker + Certbot (~5 min)

As `deploy`:
```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y docker.io docker-compose-v2 git certbot
sudo usermod -aG docker deploy
exit                      # log out so the docker group applies
```

Reconnect and verify:
```bash
docker --version
docker compose version
```

---

## Phase 5 — Clone the repo + write `.env` (~10 min)

```bash
cd ~
git clone https://github.com/bennylim0926/BES.git kyrove
cd kyrove
git checkout feat/deploy-hetzner-https     # the branch with prod configs
cp .env.example .env
nano .env
```

In `.env`, set production values. **Generate fresh strong passwords** — don't reuse local dev values.

Required:
```
DOMAIN=<DOMAIN>
BES_COOKIE_DOMAIN=<DOMAIN>
BES_SECURE_COOKIE=true
BES_ALLOWED_ORIGINS=https://<DOMAIN>,https://www.<DOMAIN>
EMAIL=<your gmail>
EMAIL_PASSWORD=<gmail app password>
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/<dbname>
SPRING_DATASOURCE_USERNAME=<db user>
SPRING_DATASOURCE_PASSWORD=<strong password>
SPRING_DATASOURCE_DBNAME=<dbname>
BES_ADMIN_PASSWORD=<strong>
BES_EMCEE_PASSWORD=<strong>
BES_JUDGE_PASSWORD=<strong>
BES_ORGANISER_PASSWORD=<strong>
```

Copy your Google API credentials from your Mac (run on Mac, not server):
```bash
scp /Users/bennylim/Documents/BES/credentials.json deploy@<SERVER_IP>:~/kyrove/
```

---

## Phase 6 — Get the TLS certificate (~5 min)

On the server, with nothing yet bound to port 80:
```bash
sudo certbot certonly --standalone \
  -d <DOMAIN> -d www.<DOMAIN> \
  --agree-tos -m <EMAIL> --no-eff-email
```

Verify the cert exists:
```bash
sudo ls /etc/letsencrypt/live/<DOMAIN>/
# expect: fullchain.pem  privkey.pem  cert.pem  chain.pem
```

---

## Phase 7 — Repo configuration (already in `feat/deploy-hetzner-https`)

Two files were added in this branch — you don't need to write them, just confirm they exist and the domain is correct.

### `docker-compose.prod.yml`
Mounts the host's `/etc/letsencrypt` into the frontend container and overrides the nginx config.

### `BES-frontend/nginx/default.prod.conf`
HTTP → HTTPS redirect, real domain `server_name`, Let's Encrypt cert paths, WebSocket upgrade.

If `server_name` still says `yourdomain.com`, swap it from your Mac and push:
```bash
sed -i '' 's/yourdomain\.com/<DOMAIN>/g' BES-frontend/nginx/default.prod.conf
git add BES-frontend/nginx/default.prod.conf
git commit -m "chore: set production domain"
git push
```

Then on the server: `git pull`.

---

## Phase 8 — First deploy (~10 min)

On the server:
```bash
cd ~/kyrove
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
docker compose ps           # all 3 services should show "Up"
docker compose logs -f backend
# wait for "Started BesApplication" — Ctrl+C
```

Test from your Mac:
```bash
curl -I https://<DOMAIN>     # expect 200, valid cert
curl -I http://<DOMAIN>      # expect 301 -> https
```

Open `https://<DOMAIN>` in a browser — green padlock, app loads.

---

## Phase 9 — Auto-renew the TLS cert (~5 min)

Let's Encrypt certs expire every 90 days. Add a renewal cron on the server:
```bash
sudo crontab -e
```
Add:
```
0 3 * * * certbot renew --pre-hook "docker stop bes_frontend" --post-hook "docker start bes_frontend" >> /var/log/certbot-renew.log 2>&1
```

Dry-run to confirm renewal logic works:
```bash
sudo certbot renew --dry-run
```

---

## Routine operations

### Deploy a new version
```bash
ssh deploy@<SERVER_IP>
cd ~/kyrove
git pull
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

### Check logs
```bash
docker compose logs -f backend
docker compose logs -f frontend
docker compose logs --tail=200 postgres
```

### Database access (read-only sanity check)
```bash
docker compose exec postgres psql -U <db user> -d <dbname>
```

### Restart everything
```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml restart
```

### Hetzner snapshot before risky changes
Hetzner Console → Server → Snapshots → "Take Snapshot". €0.01/GB/mo.

---

## Troubleshooting

### `certbot` fails with "DNS problem" / "connection refused on port 80"
- DNS hasn't propagated yet → re-run `dig +short <DOMAIN>`, must return `<SERVER_IP>`.
- Something is bound to port 80 already → `sudo ss -tlnp | grep ':80 '`. Stop it before re-running certbot.

### Browser shows "Not secure" or self-signed cert warning
- The container is still using the localhost cert from the dev `default.conf`.
- Verify `docker-compose.prod.yml` was actually included on `up`: the command must have **both** `-f` flags.
- Check inside the container:
  ```bash
  docker compose exec frontend cat /etc/nginx/conf.d/default.conf | head -5
  # should reference /etc/letsencrypt/live/<DOMAIN>/...
  ```

### WebSocket connections drop after ~60s
- The 1-hour `proxy_read_timeout` is already set in `default.prod.conf`. If issues persist, check Hetzner cloud firewall isn't dropping idle connections (it shouldn't by default).

### 502 Bad Gateway from `/api/`
- Backend container probably died — `docker compose logs backend`.
- Most common cause: bad `SPRING_DATASOURCE_*` values in `.env`, or Flyway migration mismatch.

### Site is suddenly down
1. `ssh deploy@<SERVER_IP>` — server reachable?
2. `docker compose ps` — any container exited?
3. `docker compose logs --tail=200 <service>` — root cause
4. `sudo systemctl status docker` — is the docker daemon alive?
5. Worst case: restore from Hetzner backup (Hetzner Console → Server → Backups).

---

## Phase 2 — CI/CD with GitHub Actions (deferred, do later)

Once the manual deploy is stable, add auto-deploy on push to `master`.

### Add repo secrets
GitHub repo → **Settings → Secrets and variables → Actions**:
- `HOST` — `<SERVER_IP>`
- `USERNAME` — `deploy`
- `SSH_KEY` — full contents of `~/.ssh/id_ed25519` (private key)

### Add `.github/workflows/deploy.yml`
```yaml
name: Deploy to production

on:
  push:
    branches: [master]

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - name: Deploy via SSH
        uses: appleboy/ssh-action@v1.0.3
        with:
          host: ${{ secrets.HOST }}
          username: ${{ secrets.USERNAME }}
          key: ${{ secrets.SSH_KEY }}
          script: |
            cd ~/kyrove
            git pull origin master
            docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
            docker compose ps
```

This uses `git pull` on the server (not SCP), keeping `.env` and `credentials.json` in place — they don't need to be in GitHub secrets.

---

## What's still deferred

- **Daily DB backup** to Backblaze B2 (`pg_dump` cron). Hetzner backups cover server-level recovery; per-DB dumps are better for fine-grained restore.
- **Uptime monitoring** — UptimeRobot free tier, 5-min ping on `https://<DOMAIN>`.
- **Log aggregation** — for now, `docker compose logs` is enough.

Tackle in this order after CI/CD: DB backups → uptime monitor → log aggregation.
