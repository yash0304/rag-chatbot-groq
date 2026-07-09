import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "MindQuest — Your knowledge, made legend",
  description:
    "AI-first second brain with RPG progression: documents become territories, tasks become quests, habits become daily missions.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
