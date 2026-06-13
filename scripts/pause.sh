#!/usr/bin/env bash
# Prepare the server to be snapshotted + deleted so you stop being billed.
# Run this on the Hetzner server (as the 'deploy' user) before taking the snapshot.
#
# Usage:
#   cd ~/kyrove
#   ./scripts/pause.sh
#
# Then go to Hetzner Console → Server → Snapshots → "Take snapshot".
# Wait for status "Created", verify in top-level Snapshots, then delete the server.
set -euo pipefail

cd "$(dirname "$0")/.."

echo "→ Stopping containers (volumes preserved — DB data stays on disk)..."
docker compose down

echo "→ Flushing filesystem buffers so the snapshot captures a clean state..."
sudo sync

SNAPSHOT_NAME="kyrove-paused-$(date +%F)"
cat <<EOF

================================================================
✓ Server is ready to snapshot.

Next steps (in Hetzner Cloud Console):
  1. Open your server → "Snapshots" tab → "Take snapshot"
     Name:  ${SNAPSHOT_NAME}
  2. Wait until status shows "Created" (3–5 min)
  3. Go to top-level "Snapshots" — confirm the snapshot is listed
     with non-zero size. DO NOT skip this step.
  4. Open your server → "Delete this server"

After deletion you'll be billed only ~\$0.85/mo for the snapshot.

To resume later, see docs/DEPLOYMENT.md → "Pausing the server".
================================================================
EOF
