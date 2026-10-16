package com.calypso.quizzly

import com.calypso.quizzly.game.FilaRanking
import com.calypso.quizzly.game.Periodo
import com.calypso.quizzly.game.PuntajeDePartida
import com.calypso.quizzly.game.Ranking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** Filtros por período y armado de la tabla del ranking (RF-10). */
class RankingTest {

    private val zona = ZoneId.of("America/Argentina/Buenos_Aires")

    // Viernes 25/09/2026 a las 18:30.
    private val ahora = ZonedDateTime.of(2026, 9, 25, 18, 30, 0, 0, zona)

    private fun millis(anio: Int, mes: Int, dia: Int) =
        ZonedDateTime.of(anio, mes, dia, 0, 0, 0, 0, zona).toInstant().toEpochMilli()

    @Test
    fun inicioDeCadaPeriodo() {
        assertEquals(millis(2026, 9, 25), Ranking.inicioDelPeriodo(Periodo.DIA, ahora))
        assertEquals(millis(2026, 9, 21), Ranking.inicioDelPeriodo(Periodo.SEMANA, ahora)) // lunes
        assertEquals(millis(2026, 9, 1), Ranking.inicioDelPeriodo(Periodo.MES, ahora))
        assertNull(Ranking.inicioDelPeriodo(Periodo.GLOBAL, ahora))
    }

    @Test
    fun semanaDesdeElLunes() {
        val lunes = ZonedDateTime.of(2026, 9, 21, 9, 0, 0, 0, zona)
        assertEquals(millis(2026, 9, 21), Ranking.inicioDelPeriodo(Periodo.SEMANA, lunes))
    }

    @Test
    fun sumaYOrdena() {
        val tabla = Ranking.sumarPorJugador(
            listOf(
                PuntajeDePartida("a", "magali", 40),
                PuntajeDePartida("b", "seba", 70),
                PuntajeDePartida("a", "magali", 45),
                PuntajeDePartida("c", "zoe", 10)
            )
        )
        assertEquals(
            listOf(
                FilaRanking("a", "magali", 85),
                FilaRanking("b", "seba", 70),
                FilaRanking("c", "zoe", 10)
            ),
            tabla
        )
    }

    @Test
    fun empateYNombreReciente() {
        val tabla = Ranking.sumarPorJugador(
            listOf(
                PuntajeDePartida("x", "Zeta", 30),
                PuntajeDePartida("y", "ana", 20),
                PuntajeDePartida("y", "Ana", 10)
            )
        )
        assertEquals(listOf("Ana", "Zeta"), tabla.map { it.nombre })
    }
}
