#!/usr/bin/env bash
#
# Finance Super Key — build & (re)start both apps with PM2.
# Run from anywhere; it cd's to the repo root itself.
# Assumes one-time setup is done (see deploy/DEPLOY.md): Node, PostgreSQL,
# PM2, nginx, and the two .env files already in place.
#
#   bash deploy/deploy-app.sh
#
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT="$(pwd)"
echo "Repo: $ROOT"

echo "==> git pull"
git pull origin main

echo "==> Backend: install, migrate, build"
cd "$ROOT/services/backend"
[ -f .env ] || { echo "ERROR: services/backend/.env missing (see deploy/DEPLOY.md)"; exit 1; }
npm ci
npx prisma generate
npx prisma migrate deploy
npm run build

echo "==> Admin web: install, build"
cd "$ROOT/apps/admin-web"
[ -f .env ] || { echo "ERROR: apps/admin-web/.env missing (see deploy/DEPLOY.md)"; exit 1; }
npm ci
npm run build

echo "==> PM2 start/reload"
cd "$ROOT"
pm2 startOrReload deploy/ecosystem.config.js
pm2 save

echo ""
echo "Done. Status:"
pm2 status
echo ""
echo "Test:  curl -s https://admin.deencircle.online/financekey/api/v1/health"
