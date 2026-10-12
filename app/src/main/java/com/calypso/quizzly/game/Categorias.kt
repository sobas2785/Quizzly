package com.calypso.quizzly.game

import androidx.annotation.DrawableRes
import com.calypso.quizzly.R

/**
 * Única fuente de verdad de las categorías del juego.
 * El id se usa en la base (Pregunta.categoriaId, Partida.categoriaId) y el orden
 * de la lista es el orden de los gajos de la ruleta.
 *
 * Los colores van como enteros ARGB literales (y no con Color.parseColor) para que
 * esta clase no dependa del framework de Android y pueda usarse en los tests.
 * La ilustración es solo un id de recurso, así que tampoco la ata al framework.
 */
data class Categoria(
    val id: Int,
    val nombre: String,
    /** Personaje de la categoría (RF-13): brújula, átomo, copa, tele, mapa y casete. */
    @DrawableRes val ilustracion: Int,
    val color: Int,
    val colorFondoClaro: Int,
    val colorTexto: Int
)

object Categorias {

    val todas = listOf(
        Categoria(1, "Geografía", R.drawable.personaje_geografia, 0xFF4F46E5.toInt(), 0xFFEEF2FF.toInt(), 0xFF4338CA.toInt()),
        Categoria(2, "Ciencia", R.drawable.personaje_ciencia, 0xFF7C3AED.toInt(), 0xFFF5F3FF.toInt(), 0xFF7C3AED.toInt()),
        Categoria(3, "Deportes", R.drawable.personaje_deportes, 0xFF059669.toInt(), 0xFFECFDF5.toInt(), 0xFF047857.toInt()),
        Categoria(4, "Entretenimiento", R.drawable.personaje_entretenimiento, 0xFFEA580C.toInt(), 0xFFFFF7ED.toInt(), 0xFFC2410C.toInt()),
        Categoria(5, "Historia", R.drawable.personaje_historia, 0xFFDC2626.toInt(), 0xFFFEF2F2.toInt(), 0xFFB91C1C.toInt()),
        Categoria(6, "Música", R.drawable.personaje_musica, 0xFF2563EB.toInt(), 0xFFEFF6FF.toInt(), 0xFF1D4ED8.toInt())
    )

    fun porId(id: Int): Categoria = todas.first { it.id == id }

    fun porIndice(indice: Int): Categoria = todas[indice]
}
