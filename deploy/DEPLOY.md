# Deployment — VPS (admin.deencircle.online/financekey)

এই গাইড অনুযায়ী আপনার VPS-এ (Ubuntu/Debian) পুরো সিস্টেম host হবে:

- `https://admin.deencircle.online/financekey` → Super Admin web (Next.js)
- `https://admin.deencircle.online/financekey/api/...` → Backend API (NestJS)

> সব command আপনার VPS-এ `root@200.234.32.137`-এ SSH করে চালাবেন।
> **আগে নিশ্চিত করুন:** DNS-এ `admin.deencircle.online` → `200.234.32.137` (A record) পয়েন্ট করছে।

---

## ধাপ ০ — SSH করুন
```bash
ssh root@200.234.32.137
```

## ধাপ ১ — সিস্টেম প্যাকেজ (একবার)
```bash
apt update && apt upgrade -y
# Node.js 20 LTS
curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
apt install -y nodejs git nginx postgresql postgresql-contrib
# PM2 (process manager) + certbot (SSL)
npm install -g pm2
apt install -y certbot python3-certbot-nginx
```

## ধাপ ২ — PostgreSQL (একবার)
```bash
systemctl enable --now postgresql
sudo -u postgres psql -c "CREATE USER financekey WITH PASSWORD 'CHANGE_THIS_DB_PASSWORD';"
sudo -u postgres psql -c "CREATE DATABASE financekey OWNER financekey;"
```
> `CHANGE_THIS_DB_PASSWORD` একটা শক্ত পাসওয়ার্ড দিন, নিচে `.env`-এ একই পাসওয়ার্ড বসাবেন।

## ধাপ ৩ — কোড ক্লোন
```bash
mkdir -p /opt && cd /opt
git clone https://github.com/gamexpchub-gif/financesuperkey.git financekey
cd /opt/financekey
```

## ধাপ ৪ — Backend `.env`
```bash
cat > /opt/financekey/services/backend/.env <<'EOF'
DATABASE_URL=postgresql://financekey:CHANGE_THIS_DB_PASSWORD@localhost:5432/financekey?schema=public
JWT_SECRET=CHANGE_THIS_run_openssl_rand_base64_48
JWT_REFRESH_SECRET=CHANGE_THIS_another_random_value
JWT_ACCESS_TTL=15m
JWT_REFRESH_TTL=30d
PORT=3000
PUBLIC_API_BASE=https://admin.deencircle.online/financekey/api/v1
DPC_APK_URL=https://admin.deencircle.online/financekey/dpc/customer-app.apk
EOF
```
শক্ত secret বানাতে: `openssl rand -base64 48` — দুবার চালিয়ে দুটো মান বসান।

## ধাপ ৫ — Admin web `.env`
```bash
cat > /opt/financekey/apps/admin-web/.env <<'EOF'
NEXT_PUBLIC_API_BASE=https://admin.deencircle.online/financekey/api/v1
NEXT_PUBLIC_BASE_PATH=/financekey
EOF
```

## ধাপ ৬ — Build + চালু (PM2)
```bash
cd /opt/financekey
bash deploy/deploy-app.sh          # install, migrate, build, pm2 start
# প্রথমবার seed চালান (super admin, merchant, 500 key তৈরি করে):
cd services/backend && npm run db:seed && cd /opt/financekey
# রিবুটেও চালু থাকবে:
pm2 startup systemd -u root --hp /root
pm2 save
```

## ধাপ ৭ — nginx
```bash
cp /opt/financekey/deploy/nginx-financekey.conf /etc/nginx/sites-available/financekey
ln -sf /etc/nginx/sites-available/financekey /etc/nginx/sites-enabled/financekey
nginx -t && systemctl reload nginx
```
> যদি `admin.deencircle.online`-এর জন্য আগে থেকে একটা server block থাকে, তাহলে নতুন ফাইল যোগ না করে ওই block-এর ভেতরে শুধু দুটো `location /financekey ...` অংশ কপি করুন।

## ধাপ ৮ — SSL (HTTPS)
```bash
certbot --nginx -d admin.deencircle.online
```
certbot নিজে HTTPS যোগ করে ও auto-renew সেট করে।

---

## ✅ যাচাই
```bash
pm2 status
curl -s https://admin.deencircle.online/financekey/api/v1/health
```
`{"status":"ok",...}` এলেই ঠিক আছে।

এরপর ব্রাউজারে খুলুন:
**https://admin.deencircle.online/financekey**
লগইন: `01700000000` / `Admin@12345`  → **প্রথম লগইনের পর পাসওয়ার্ড বদলান।**

---

## আপডেট (পরে নতুন কোড push হলে)
```bash
cd /opt/financekey && bash deploy/deploy-app.sh
```

## সমস্যা হলে
```bash
pm2 logs financekey-api --lines 50     # backend লগ
pm2 logs financekey-admin --lines 50   # admin লগ
tail -n 50 /var/log/nginx/error.log
```

## 🔒 নিরাপত্তা (production-এ অবশ্যই)
- Seed-এর ডিফল্ট পাসওয়ার্ড (`Admin@12345`) **সাথে সাথে বদলান**।
- `JWT_SECRET`/`JWT_REFRESH_SECRET` অবশ্যই random, গোপন।
- Firewall: শুধু 80/443 (আর আপনার SSH port) খোলা রাখুন; 3000/3100 বাইরে বন্ধ (nginx-ই proxy করে)।
  ```bash
  ufw allow 22 && ufw allow 80 && ufw allow 443 && ufw enable
  ```
- `.env` ফাইল কখনো git-এ যাবে না (ignore করা আছে)।
