import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "TriForge",
  description: "Triathlon Move-to-Earn and GameFi training ledger.",
  icons: {
    icon: "/triforge.svg"
  }
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
