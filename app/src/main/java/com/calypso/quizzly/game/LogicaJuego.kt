package com.calypso.quizzly.game

import kotlin.random.Random

/** Cómo terminó la partida, para elegir el sonido del resultado. */
enum class Desenlace { GANADA, PERDIDA, EMPATE }

/** Un logro de la galería del perfil. El código es el que se guarda en logros_usuario. */
data class Logro(val codigo: String, val nombre: String, val descripcion: String, val icono: String)

/** Las cuatro opciones de una pregunta, en el orden de los botones A, B, C y D, y la letra de la correcta. */
data class Opciones(val textos: List<String>, val correcta: String)

/**
 * Reglas del juego, sin dependencias de Android ni de Room, para poder testearlas aparte.
 */
object LogicaJuego {

    const val PREGUNTAS_POR_PARTIDA = 5
    const val SEGUNDOS_POR_PREGUNTA = 15
    const val PUNTOS_BASE = 10
    const val PUNTOS_POR_NIVEL = 200

    /** Duelo local (P7): cuántas preguntas responde cada jugador, a elegir en la sala de duelo. */
    val RONDAS_DE_DUELO = listOf(5, 10, 15)
    const val RONDAS_DE_DUELO_POR_DEFECTO = 5
    const val LARGO_MAXIMO_DE_NOMBRE = 20

    /** Puntos de una respuesta correcta: base + bonus por tiempo restante + bonus por racha. */
    fun puntosDeRespuesta(segundosRestantes: Int, rachaActual: Int): Int {
        val bonusTiempo = segundosRestantes.coerceAtLeast(0)
        val bonusRacha = (rachaActual - 1).coerceAtLeast(0) * 5
        return PUNTOS_BASE + bonusTiempo + bonusRacha
    }

    /**
     * RF-05: las opciones se muestran en orden aleatorio, para que la correcta no esté siempre en la
     * misma letra. Devuelve las opciones mezcladas y la letra donde quedó la correcta.
     */
    fun mezclarOpciones(textos: List<String>, correcta: String, azar: Random = Random): Opciones {
        val textoCorrecto = textos["ABCD".indexOf(correcta)]
        val mezclados = textos.shuffled(azar)
        return Opciones(mezclados, "ABCD"[mezclados.indexOf(textoCorrecto)].toString())
    }

    /** P7: los nombres del duelo local. Devuelve el error a mostrar, o null si se puede empezar. */
    fun errorEnNombresDeDuelo(nombre1: String, nombre2: String): String? = when {
        nombre1.isBlank() || nombre2.isBlank() -> "Completá los nombres de los dos jugadores"
        nombre1.trim().equals(nombre2.trim(), ignoreCase = true) -> "Los dos jugadores necesitan nombres distintos"
        else -> null
    }

    /** El nivel sube cada 200 puntos acumulados. Nivel mínimo: 1. */
    fun nivelPara(puntosTotales: Int): Int = (puntosTotales / PUNTOS_POR_NIVEL) + 1

    /** RF-07: la dificultad de las preguntas escala con el nivel del jugador. */
    fun dificultadPara(nivel: Int): String = when {
        nivel <= 2 -> "Facil"
        nivel <= 5 -> "Medio"
        else -> "Dificil"
    }

    /** En la partida individual se gana acertando más de la mitad (3 de 5): errar 3 o más es perder. */
    fun desenlaceIndividual(correctas: Int, totalPreguntas: Int): Desenlace =
        if (correctas * 2 > totalPreguntas) Desenlace.GANADA else Desenlace.PERDIDA

    /** Rachas que se festejan con sonido propio: las mismas de los logros "primera_racha" y "en_llamas". */
    fun esRachaDestacada(racha: Int): Boolean = racha == 3 || racha == 5

    /**
     * Logros que corresponden a una partida recién terminada.
     * Devuelve los códigos a insertar en logros_usuario (el llamador ignora los repetidos).
     */
    fun logrosDeLaPartida(
        correctas: Int,
        totalPreguntas: Int,
        rachaMaxima: Int,
        partidasJugadas: Int,
        categoriasJugadas: Int
    ): List<String> {
        val logros = mutableListOf<String>()
        if (partidasJugadas >= 1) logros += "primera_partida"
        if (rachaMaxima >= 3) logros += "primera_racha"
        if (rachaMaxima >= 5) logros += "en_llamas"
        if (correctas == totalPreguntas && totalPreguntas > 0) logros += "partida_perfecta"
        if (partidasJugadas >= 10) logros += "veterano"
        if (categoriasJugadas >= Categorias.todas.size) logros += "todoterreno"
        return logros
    }

    /** Todos los logros, en el orden en que se muestran en la galería del perfil (RF-12). */
    val logros = listOf(
        Logro("primera_partida", "Primera partida", "Terminá tu primera partida", "🎮"),
        Logro("primera_racha", "Racha de 3", "Acertá 3 preguntas seguidas", "⚡"),
        Logro("en_llamas", "En llamas (racha de 5)", "Acertá 5 preguntas seguidas", "🔥"),
        Logro("partida_perfecta", "Partida perfecta", "Respondé bien todas las preguntas de una partida", "⭐"),
        Logro("veterano", "Veterano (10 partidas)", "Jugá 10 partidas", "🎖️"),
        Logro("todoterreno", "Todoterreno (todas las categorías)", "Jugá en las 6 categorías", "🧭")
    )

    val nombresDeLogros = logros.associate { it.codigo to it.nombre }

    /** Puntos que ya se juntaron dentro del nivel actual, de 0 a PUNTOS_POR_NIVEL - 1. */
    fun puntosDentroDelNivel(puntosTotales: Int): Int = puntosTotales % PUNTOS_POR_NIVEL
}
