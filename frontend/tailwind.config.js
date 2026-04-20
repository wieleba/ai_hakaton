/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      // Discord-style dark palette — use as `bg-discord-base`, `text-discord-text`, etc.
      colors: {
        discord: {
          deep: '#202225',    // deepest (top nav shadow, page bg)
          sidebar: '#2F3136', // left sidebar
          base: '#36393F',    // main chat surface
          hover: '#3F4147',   // hover for sidebar items
          input: '#40444B',   // input / elevated surfaces
          border: '#42464D',  // dividers
          text: '#DCDDDE',    // primary text
          muted: '#B9BBBE',   // secondary text
          dim: '#72767D',     // tertiary text
          accent: '#5865F2',  // blurple
        },
      },
    },
  },
  plugins: [],
}
