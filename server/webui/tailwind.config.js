module.exports = {
  content: [
    "./*/templates/**/*.html",
    "./core/templates/**/*.html",
    "./**/*.py",
  ],
  darkMode: "class",
  theme: {
    extend: {
      colors: {
        teal: { 600: "#00897B", 900: "#004D40" },
      },
    },
  },
  plugins: [],
}
