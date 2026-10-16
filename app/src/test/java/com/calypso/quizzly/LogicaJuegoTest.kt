package com.calypso.quizzly

import com.calypso.quizzly.game.Desenlace
import com.calypso.quizzly.game.LogicaJuego
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Reglas de puntaje, nivel, dificultad y logros. */
class LogicaJuegoTest {

    @Test
    fun puntosPorRapidezYRacha() {
        assertEquals(25, LogicaJuego.puntosDeRespuesta(segundosRestantes = 15, rachaActual = 1))
        assertEquals(10, LogicaJuego.puntosDeRespuesta(segundosRestantes = 0, rachaActual = 1))
        // Racha de 3: 10 base + 5 seg + 10 de bonus por racha.
        assertEquals(25, LogicaJuego.puntosDeRespuesta(segundosRestantes = 5, rachaActual = 3))
    }

    @Test
    fun nivelCada200Puntos() {
        assertEquals(1, LogicaJuego.nivelPara(0))
        assertEquals(1, LogicaJuego.nivelPara(199))
        assertEquals(2, LogicaJuego.nivelPara(200))
        assertEquals(4, LogicaJuego.nivelPara(650))
    }

    @Test
    fun dificultadPorNivel() {
        assertEquals("Facil", LogicaJuego.dificultadPara(1))
        assertEquals("Facil", LogicaJuego.dificultadPara(2))
        assertEquals("Medio", LogicaJuego.dificultadPara(3))
        assertEquals("Dificil", LogicaJuego.dificultadPara(6))
    }

    @Test
    fun otorgaLogros() {
        val primeraPartidaFloja = LogicaJuego.logrosDeLaPartida(
            correctas = 2,
            totalPreguntas = 5,
            rachaMaxima = 1,
            partidasJugadas = 1,
            categoriasJugadas = 1
        )
        assertTrue("primera_partida" in primeraPartidaFloja)
        assertFalse("primera_racha" in primeraPartidaFloja)
        assertFalse("partida_perfecta" in primeraPartidaFloja)

        val partidaPerfecta = LogicaJuego.logrosDeLaPartida(
            correctas = 5,
            totalPreguntas = 5,
            rachaMaxima = 5,
            partidasJugadas = 10,
            categoriasJugadas = 6
        )
        assertTrue("primera_racha" in partidaPerfecta)
        assertTrue("en_llamas" in partidaPerfecta)
        assertTrue("partida_perfecta" in partidaPerfecta)
        assertTrue("veterano" in partidaPerfecta)
        assertTrue("todoterreno" in partidaPerfecta)
    }

    @Test
    fun tresErroresPierde() {
        assertEquals(Desenlace.GANADA, LogicaJuego.desenlaceIndividual(correctas = 5, totalPreguntas = 5))
        assertEquals(Desenlace.GANADA, LogicaJuego.desenlaceIndividual(correctas = 3, totalPreguntas = 5))
        assertEquals(Desenlace.PERDIDA, LogicaJuego.desenlaceIndividual(correctas = 2, totalPreguntas = 5))
        assertEquals(Desenlace.PERDIDA, LogicaJuego.desenlaceIndividual(correctas = 0, totalPreguntas = 5))
    }

    @Test
    fun rachasConSonido() {
        assertFalse(LogicaJuego.esRachaDestacada(2))
        assertTrue(LogicaJuego.esRachaDestacada(3))
        assertFalse(LogicaJuego.esRachaDestacada(4))
        assertTrue(LogicaJuego.esRachaDestacada(5))
    }

    @Test
    fun mezclaDeOpciones() {
        val textos = listOf("Venus", "Marte", "Júpiter", "Mercurio")
        val letrasCorrectas = mutableSetOf<String>()

        repeat(40) { semilla ->
            val mezcla = LogicaJuego.mezclarOpciones(textos, correcta = "B", azar = Random(semilla))

            assertEquals(textos.toSet(), mezcla.textos.toSet())
            assertEquals("Marte", mezcla.textos["ABCD".indexOf(mezcla.correcta)])
            letrasCorrectas += mezcla.correcta
        }

        // Con 40 mezclas distintas, la correcta tiene que haber caído en las cuatro letras.
        assertEquals(setOf("A", "B", "C", "D"), letrasCorrectas)
    }

    @Test
    fun nombresDeDuelo() {
        assertEquals(null, LogicaJuego.errorEnNombresDeDuelo("Seba", "Maga"))
        assertTrue(LogicaJuego.errorEnNombresDeDuelo("Seba", " ") != null)
        assertTrue(LogicaJuego.errorEnNombresDeDuelo("", "Maga") != null)
        assertTrue("Mayúsculas y espacios no cuentan", LogicaJuego.errorEnNombresDeDuelo("seba ", "Seba") != null)
    }

    @Test
    fun partidaVaciaNoEsPerfecta() {
        val sinPreguntas = LogicaJuego.logrosDeLaPartida(
            correctas = 0,
            totalPreguntas = 0,
            rachaMaxima = 0,
            partidasJugadas = 3,
            categoriasJugadas = 2
        )
        assertFalse("partida_perfecta" in sinPreguntas)
    }
}
