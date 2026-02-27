---
description: Build Docker images and deploy to production
---

# Deploy

## 1. Run All Tests

```bash
# Backend tests
cd BES && ./mvnw test

# Frontend tests
cd BES-frontend && npm test
```

**Do NOT proceed if any tests fail.**

## 2. Build Docker Images

```bash
# Build the backend JAR first
cd BES && ./mvnw clean package -DskipTests

# Build all Docker images
docker compose build
```

## 3. Local Smoke Test

```bash
docker compose up -d

# Wait for backend startup
sleep 10

# Check all containers are running
docker compose ps

# Check backend logs for errors
docker compose logs backend | tail -20

# Quick API health check
curl -k https://localhost/api/v1/auth/me
```

## 4. Push to Production

```bash
# SSH to the EC2 server
ssh -i beskey.pem ubuntu@<EC2_IP>

# On the server:
cd /path/to/BES
git pull
docker compose build
docker compose up -d

# Verify
docker compose ps
docker compose logs backend | tail -20
```

## 5. Post-Deploy Verification

- Check the live site is accessible
- Verify login works
- Check browser console for JavaScript errors
- Monitor backend logs for 5 minutes: `docker compose logs -f backend`
