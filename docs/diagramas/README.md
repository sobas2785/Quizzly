# Diagramas de clases de Quizzly

Cada diagrama está en dos formatos:

- `.mmd`: el código fuente en [Mermaid](https://mermaid.js.org/), que es texto y se versiona con el proyecto.
- `.png`: la imagen para el documento, a doble resolución.
- `.svg`: la versión vectorial, que se puede ampliar sin perder calidad.

| Archivo | Qué muestra |
|---|---|
| `00-arquitectura` | Vista general: los paquetes de la app y cómo dependen entre sí |
| `01-modelo-de-datos` | Las 4 entidades de Room y sus relaciones, más Categoria y Logro |
| `02-acceso-a-datos` | AppDatabase, los DAO y los repositorios |
| `03-reglas-del-juego` | LogicaJuego, Categorias y el cierre de partida (finalizarPartida) |
| `04-pantallas-y-viewmodels` | Cada Activity con su ViewModel y la herencia de los ViewModels |
| `05-partida` | Detalle de la partida: ruleta, ronda de preguntas y sus estados |
| `06-ranking-online` | Ranking con Firebase y los filtros por período |
| `07-duelo-online` | Duelo online por turnos con código de invitación |
| `08-utilidades` | Sesion, Sonidos, Vibracion, HashUtils y Efectos |

## Cómo editar un diagrama

1. Abrir el `.mmd` y copiar su contenido en [mermaid.live](https://mermaid.live).
2. Hacer los cambios y ver el resultado al instante.
3. Pegar el texto corregido en el `.mmd` y descargar la imagen nueva desde la misma página (PNG o SVG).
