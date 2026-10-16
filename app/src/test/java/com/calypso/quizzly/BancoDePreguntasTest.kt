package com.calypso.quizzly

import com.calypso.quizzly.game.Categorias
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Revisa assets/preguntas.json, que se edita a mano: que cada pregunta esté bien armada
 * y que el banco tenga 30 preguntas por categoría, 10 de cada dificultad.
 */
class BancoDePreguntasTest {

    private val preguntas: List<JSONObject> by lazy {
        // Los tests corren con el módulo app como directorio de trabajo.
        val array = JSONArray(File("src/main/assets/preguntas.json").readText())
        (0 until array.length()).map { array.getJSONObject(it) }
    }

    @Test
    fun opcionesValidas() {
        preguntas.forEach { p ->
            val texto = p.getString("texto")
            val opciones = p.getJSONArray("opciones").let { o -> (0 until o.length()).map { o.getString(it) } }

            assertEquals("Opciones de: $texto", 4, opciones.size)
            assertEquals("Opciones repetidas en: $texto", 4, opciones.toSet().size)
            assertTrue("Respuesta inválida en: $texto", p.getString("respuesta") in listOf("A", "B", "C", "D"))
            assertTrue("Dificultad inválida en: $texto", p.getString("dificultad") in listOf("Facil", "Medio", "Dificil"))
            assertTrue("Categoría inexistente en: $texto", Categorias.todas.any { it.id == p.getInt("categoriaId") })
        }
    }

    @Test
    fun sinRepetidas() {
        val textos = preguntas.map { it.getString("texto").trim().lowercase() }
        assertEquals(textos.size, textos.toSet().size)
    }

    @Test
    fun diezPorDificultad() {
        Categorias.todas.forEach { categoria ->
            listOf("Facil", "Medio", "Dificil").forEach { dificultad ->
                val cantidad = preguntas.count {
                    it.getInt("categoriaId") == categoria.id && it.getString("dificultad") == dificultad
                }
                assertEquals("${categoria.nombre} · $dificultad", 10, cantidad)
            }
        }
    }
}
