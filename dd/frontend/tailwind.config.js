/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        'dd-bg-primary': '#C7F464', // Parrot Green
        'dd-surface': '#FFFFFF',
        'dd-accent-blue': '#2563EB', // Motivating Blue
        'dd-accent-indigo': '#6366F1', // Energetic Indigo
        'dd-text-main': '#1E293B',
        'dd-text-muted': '#64748B',
      },
      boxShadow: {
        'soft': '0 4px 20px rgba(0, 0, 0, 0.05)',
        'soft-hover': '0 10px 25px rgba(0, 0, 0, 0.08)',
        'glow-blue': '0 0 15px rgba(37, 99, 235, 0.3)',
      }
    },
  },
  plugins: [],
}
