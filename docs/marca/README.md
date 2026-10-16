# Marca de Quizzly

| Archivo | Qué es |
|---|---|
| `original-simbolo.png` | El símbolo (la Q con la ruleta de las 6 categorías) tal como salió del diseño, sobre fondo blanco. |
| `original-logo.png` | El logo completo, con la palabra "Quizzly", sobre fondo blanco. |
| `quizzly-simbolo.png` | El símbolo con fondo transparente, recortado. |
| `quizzly-logo.png` | El logo completo con fondo transparente, recortado. |

Los gajos de la ruleta son las categorías del juego, con sus mismos colores: Geografía (mundo),
Ciencia (tubo de ensayo), Deportes (corredor), Entretenimiento (claqueta), Historia (columna) y
Música (nota). La cola de la Q es la flecha que marca la categoría.

## Dónde se usa en la app

- **Ícono:** `res/mipmap-*/ic_launcher_foreground.png`, el símbolo sobre el degradado violeta de
  `res/drawable/ic_launcher_background.xml`. El ícono monocromático (íconos temáticos de Android 13 o
  superior) usa la Q sola de `res/drawable/ic_launcher_monocromo.xml`.
- **Pantalla de inicio y login:** `res/drawable-xxxhdpi/logo_quizzly.png`.

Si el logo cambia, hay que volver a generar a partir de los originales los archivos sin fondo y los
recursos de la app.
