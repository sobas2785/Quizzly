package com.calypso.quizzly.game

import kotlin.random.Random

/** En qué momento está un duelo online. El texto es el que se guarda en Firestore. */
enum class EstadoDuelo(val clave: String) {
    ESPERANDO("esperando"),
    EN_CURSO("en_curso"),
    TERMINADO("terminado");

    companion object {
        fun deClave(clave: String?): EstadoDuelo = entries.firstOrNull { it.clave == clave } ?: ESPERANDO
    }
}

/**
 * Un duelo online por turnos, tal como está guardado en Firestore (colección "duelos").
 * El Jugador 1 es quien lo creó; el 2, quien se unió con el código.
 * Un jugador se identifica por su jugadorId, el mismo del ranking: uid de Firebase + id del usuario local.
 */
data class DueloOnline(
    val codigo: String,
    val estado: EstadoDuelo,
    /** A quién le toca jugar: 1 o 2. */
    val turno: Int,
    val turnosJugados: Int,
    val jugadorId1: String,
    val nombre1: String,
    val puntos1: Int,
    val correctas1: Int,
    val jugadorId2: String?,
    val nombre2: String?,
    val puntos2: Int,
    val correctas2: Int,
    val ultimaCategoriaId: Int,
    val actualizado: Long
) {
    /** 1 o 2 según qué jugador sea, o null si ese jugador no participa de este duelo. */
    fun numeroDe(jugadorId: String): Int? = when (jugadorId) {
        jugadorId1 -> 1
        jugadorId2 -> 2
        else -> null
    }

    fun esTurnoDe(jugadorId: String): Boolean =
        estado == EstadoDuelo.EN_CURSO && numeroDe(jugadorId) == turno

    fun puntosDe(numero: Int): Int = if (numero == 1) puntos1 else puntos2

    fun correctasDe(numero: Int): Int = if (numero == 1) correctas1 else correctas2

    fun nombreDe(numero: Int): String = (if (numero == 1) nombre1 else nombre2) ?: "Rival"

    /** El nombre del otro jugador, o null si todavía no se unió nadie. */
    fun rivalDe(jugadorId: String): String? = if (numeroDe(jugadorId) == 1) nombre2 else nombre1

    /** Cómo le fue a ese jugador, o null si el duelo no terminó o no es suyo. */
    fun desenlacePara(jugadorId: String): Desenlace? {
        val numero = numeroDe(jugadorId) ?: return null
        if (estado != EstadoDuelo.TERMINADO) return null

        val mios = puntosDe(numero)
        val suyos = puntosDe(3 - numero)
        return when {
            mios > suyos -> Desenlace.GANADA
            mios < suyos -> Desenlace.PERDIDA
            else -> Desenlace.EMPATE
        }
    }
}

/** Reglas del duelo online, sin dependencias de Android ni de Firebase, para poder testearlas aparte. */
object ReglasDueloOnline {

    /** Igual que el duelo local: tres turnos por jugador, de una pregunta cada uno. */
    const val TURNOS_POR_JUGADOR = 3
    const val TURNOS_TOTALES = TURNOS_POR_JUGADOR * 2

    const val LARGO_DEL_CODIGO = 6

    // Sin 0/O ni 1/I/L, que se confunden al dictar o copiar el código a mano.
    private const val LETRAS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    fun nuevoCodigo(azar: Random = Random): String =
        (1..LARGO_DEL_CODIGO).map { LETRAS[azar.nextInt(LETRAS.length)] }.joinToString("")

    /** Deja el código como se guarda (mayúsculas, sin espacios ni guiones), o null si no puede ser un código. */
    fun normalizarCodigo(texto: String): String? {
        val codigo = texto.uppercase().filter { it.isLetterOrDigit() }
        return codigo.takeIf { it.length == LARGO_DEL_CODIGO && it.all { letra -> letra in LETRAS } }
    }

    /** El estado en que queda el duelo después de jugarse un turno más. */
    fun estadoTrasElTurno(turnosJugadosAntes: Int): EstadoDuelo =
        if (turnosJugadosAntes + 1 >= TURNOS_TOTALES) EstadoDuelo.TERMINADO else EstadoDuelo.EN_CURSO

    /**
     * El orden de la pantalla "Mis duelos": primero donde le toca a ese jugador, después los que
     * esperan al rival y al final los terminados; dentro de cada grupo, lo más reciente arriba.
     */
    fun ordenar(duelos: List<DueloOnline>, jugadorId: String): List<DueloOnline> =
        duelos.sortedWith(
            compareBy<DueloOnline> {
                when {
                    it.esTurnoDe(jugadorId) -> 0
                    it.estado != EstadoDuelo.TERMINADO -> 1
                    else -> 2
                }
            }.thenByDescending { it.actualizado }
        )
}
