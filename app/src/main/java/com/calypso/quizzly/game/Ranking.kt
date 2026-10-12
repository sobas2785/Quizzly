package com.calypso.quizzly.game

import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/** Filtros del ranking (RF-10). */
enum class Periodo(val etiqueta: String) {
    DIA("Hoy"),
    SEMANA("Semana"),
    MES("Mes"),
    GLOBAL("Global")
}

/** Los puntos de una partida y de quién son, venga de Firebase o de la base local. */
data class PuntajeDePartida(val jugadorId: String, val nombre: String, val puntos: Int)

data class FilaRanking(val jugadorId: String, val nombre: String, val puntos: Int)

/** Reglas del ranking, sin dependencias de Android ni de Firebase, para poder testearlas aparte. */
object Ranking {

    const val TAMANO = 50

    /**
     * Desde cuándo cuentan las partidas del período, en milisegundos: el día arranca a las 00:00,
     * la semana el lunes y el mes el día 1. Para el global no hay límite y devuelve null.
     */
    fun inicioDelPeriodo(periodo: Periodo, ahora: ZonedDateTime): Long? {
        val hoy = ahora.toLocalDate()
        val inicio = when (periodo) {
            Periodo.DIA -> hoy
            Periodo.SEMANA -> hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            Periodo.MES -> hoy.withDayOfMonth(1)
            Periodo.GLOBAL -> return null
        }
        return inicio.atStartOfDay(ahora.zone).toInstant().toEpochMilli()
    }

    /** Suma los puntos de cada jugador y los ordena de mayor a menor (a igual puntaje, por nombre). */
    fun sumarPorJugador(partidas: List<PuntajeDePartida>): List<FilaRanking> =
        partidas.groupBy { it.jugadorId }
            .map { (jugadorId, suyas) ->
                // El nombre más reciente es el de la última partida de la lista.
                FilaRanking(jugadorId, suyas.last().nombre, suyas.sumOf { it.puntos })
            }
            .let(::ordenar)

    fun ordenar(filas: List<FilaRanking>): List<FilaRanking> =
        filas.sortedWith(compareByDescending<FilaRanking> { it.puntos }.thenBy { it.nombre.lowercase() })
            .take(TAMANO)
}
