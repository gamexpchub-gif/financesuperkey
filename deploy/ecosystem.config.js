// PM2 process config for Finance Super Key.
// Run from the repo root:  pm2 start deploy/ecosystem.config.js
module.exports = {
  apps: [
    {
      name: 'financekey-api',
      cwd: './services/backend',
      script: 'dist/main.js',
      env: { NODE_ENV: 'production', PORT: 3000 },
      max_restarts: 10,
      time: true,
    },
    {
      name: 'financekey-admin',
      cwd: './apps/admin-web',
      script: 'node_modules/next/dist/bin/next',
      args: 'start -p 3100',
      env: { NODE_ENV: 'production', PORT: 3100 },
      max_restarts: 10,
      time: true,
    },
  ],
};
