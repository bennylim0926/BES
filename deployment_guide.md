# BES Deployment Guide: Free/Low-Cost Hosting with GitHub Actions

This guide will walk you through deploying your Spring Boot + Vue.js + PostgreSQL application. Since you are using Docker Compose, the most straightforward and cost-effective approach is to use a **Virtual Private Server (VPS)**. 

## 1. Hosting Architecture Choice

Because Spring Boot + PostgreSQL can be slightly memory-intensive (often needing at least 1GB to run comfortably), platform-as-a-service free tiers (like Heroku or Render) either don't support Docker-compose easily or will spin down constantly. 

**Recommended Options:**
1. **AWS EC2 (Free Tier)**: Free for 12 months (`t2.micro` or `t3.micro` instances provide 1GB RAM).
2. **Hetzner / DigitalOcean**: If you want something permanent and reliable without a 12-month limit, a basic entry-level server costs about **$4 - $6 / month**.

Since you don't have a domain, you can simply access your website via the **Server's Public IP Address** (e.g., `http://192.168.1.100`).

---

## 2. Setting up the Server

Regardless of whether you choose AWS, DigitalOcean, or Hetzner, the steps are largely the same.

### Step 2.1: Spin up the server
- OS: **Ubuntu 22.04 LTS** or **24.04 LTS**.
- Once the server is running, note down the **Public IP Address**.
- You will be given an SSH Key pair during creation. Keep the `.pem` private key safe.

### Step 2.2: Prepare the Server (SSH into it)
Open your terminal and SSH into the server:
```bash
ssh -i /path/to/your-key.pem ubuntu@<YOUR_PUBLIC_IP>
```

### Step 2.3: Add a Swap File (CRITICAL)
Servers with 1GB RAM will run out of memory when starting Spring Boot with an active DB. Adding a swap file prevents the server from crashing:
```bash
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
# Make it permanent across reboots
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

### Step 2.4: Install Docker and Docker Compose
Run the following commands on your server to install Docker:
```bash
# Add Docker's official GPG key:
sudo apt-get update
sudo apt-get install ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc

# Add the repository to Apt sources:
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update

# Install Docker
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Make docker run without sudo
sudo usermod -aG docker $USER
```
*(After this, exit the SSH session and log back in so the permissions apply)*

### Step 2.5: Clone your project on the server
Create the folder where your project will live:
```bash
mkdir -p /home/ubuntu/BES
```
*(We will let GitHub actions automate placing the files here.)*

---

## 3. GitHub Actions CI/CD Integration

We'll set up a GitHub Workflow that triggers every time you push to the `main` branch. It will SSH into your server, copy the latest files, reconstruct your `.env` and `credentials.json` file securely, and restart the Docker containers.

### Step 3.1: Add Secrets to your GitHub Repository
Go to your project repository on GitHub web -> **Settings** -> **Secrets and variables** -> **Actions** -> **New repository secret**.

Add the following secrets:
* `HOST`: Your server's Public IP address.
* `USERNAME`: `ubuntu` (or `root` if using DigitalOcean).
* `SSH_KEY`: The entire contents of your private `.pem` SSH key file that you downloaded when creating the server.
* `ENV_FILE`: The contents of your `.env` file (containing EMAIL, SPRING_DATASOURCE_URL, etc.). Set the URL variable inside to `jdbc:postgresql://postgres:5432/<dbname>` and the domain to your public IP.
* `GOOGLE_CREDENTIALS_JSON`: The entire contents of your `credentials.json` file. (Never commit this to your public codebase).

### Step 3.2: Create the CI/CD Pipeline Configuration
Create `.github/workflows/deploy.yml` in your project with the following code.

```yaml
name: Deploy to Server

on:
  push:
    branches:
      - main # Triggers on every push to the main branch

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout Code
        uses: actions/checkout@v4

      - name: Copy files to server via SCP
        uses: appleboy/scp-action@v0.1.7
        with:
          host: ${{ secrets.HOST }}
          username: ${{ secrets.USERNAME }}
          key: ${{ secrets.SSH_KEY }}
          source: "."
          target: "/home/ubuntu/BES"
          rm: true # Clean up old files before copying

      - name: Execute deployment commands via SSH
        uses: appleboy/ssh-action@v1.0.3
        with:
          host: ${{ secrets.HOST }}
          username: ${{ secrets.USERNAME }}
          key: ${{ secrets.SSH_KEY }}
          script: |
            cd /home/ubuntu/BES
            
            # Create the .env file from GitHub Secrets
            echo "${{ secrets.ENV_FILE }}" > .env
            
            # Create the credentials.json file securely
            echo '${{ secrets.GOOGLE_CREDENTIALS_JSON }}' > credentials.json
            
            # Re-build and start the containers
            docker compose down
            docker compose up -d --build
```

---

## 4. Verification & Testing

Once you commit and push the newly added `.github/workflows/deploy.yml` to your repository:

1. **Check the Build**: Go to the **Actions** tab in your GitHub repository. You will see the job currently running.
2. **Access your site**: Once the job successfully completes, open your browser and navigate to `http://<YOUR_PUBLIC_IP>` (since your frontend binds to port 80).
3. **Continuous Deployment**: Going forward, anytime you make changes to your frontend or backend and push to `main`, GitHub will automatically push the updates to the server and spin up the new version.

### Final Checklist for frontend configuration
Your frontend code seems to connect to the backend. Make sure your frontend environment variables point your API calls toward `http://<YOUR_PUBLIC_IP>` instead of `localhost`. When deploying to production, this usually involves updating the `.env.production` in your `BES-frontend` folder to use the generic server IP.
