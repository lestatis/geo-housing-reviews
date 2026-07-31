import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // The admin app talks to the API over the network; nothing is bundled from it at build time.
  reactStrictMode: true,
};

export default nextConfig;
