// Tailwind a été retiré : le site utilise le CSS de la maquette copié verbatim
// dans app/[locale]/globals.css. Réintroduire Tailwind ferait réapparaître les
// collisions de noms de classes (ex. mb-20) et les échecs silencieux
// (border-none, modificateurs d'opacité sur variables CSS).
module.exports = {
  plugins: {
    autoprefixer: {},
  },
};
