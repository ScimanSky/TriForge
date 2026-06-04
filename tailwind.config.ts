import type { Config } from "tailwindcss";

const config: Config = {
  content: ["./app/**/*.{ts,tsx}", "./components/**/*.{ts,tsx}", "./lib/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        graphite: "#101415",
        panel: "#171d1f",
        forge: "#f5a524",
        current: "#2de2e6",
        charge: "#b7ff3c",
        ember: "#ff5d2e"
      },
      fontFamily: {
        display: ["var(--font-display)", "Georgia", "serif"],
        body: ["var(--font-body)", "Arial", "sans-serif"]
      },
      boxShadow: {
        glow: "0 0 40px rgb(45 226 230 / 0.22)",
        forge: "0 0 36px rgb(245 165 36 / 0.2)"
      }
    }
  },
  plugins: []
};

export default config;
