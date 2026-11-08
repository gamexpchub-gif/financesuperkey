/** @type {import('next').NextConfig} */

// Served under a sub-path in production (e.g. /financekey). Driven by env so
// local dev stays at the root. basePath must match the nginx location.
const basePath = process.env.NEXT_PUBLIC_BASE_PATH || '';

const nextConfig = {
  reactStrictMode: true,
  basePath: basePath || undefined,
  // Run behind a reverse proxy at a sub-path; keep asset URLs under basePath.
  assetPrefix: basePath || undefined,
};

export default nextConfig;
