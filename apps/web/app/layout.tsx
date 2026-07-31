import type { ReactNode } from "react";

export const metadata = {
  title: "Geo Housing Reviews — moderation",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
