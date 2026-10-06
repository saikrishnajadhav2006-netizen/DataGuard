/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        dg_lavender: '#A89BBE',
        dg_beige: '#DBC3A8',
        dg_sage: '#A3B18A',
        dg_cream: '#F2E8CF',
        dg_charcoal: '#333333'
      },
      fontFamily: {
        sans: ['Inter', 'sans-serif']
      }
    },
  },
  plugins: [],
}
