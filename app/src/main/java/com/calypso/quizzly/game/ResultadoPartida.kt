package com.calypso.quizzly.game

import android.content.Context
import com.calypso.quizzly.data.local.entity.Partida
import com.calypso.quizzly.data.remote.JugadorPublicado
import com.calypso.quizzly.data.remote.RankingRemoto
import com.calypso.quizzly.data.repository.LogroRepository
import com.calypso.quizzly.data.repository.PartidaRepository
import com.calypso.quizzly.data.repository.UsuarioRepository

/** Lo que devuelve una partida terminada, ya guardada en la base. */
data class ResumenPartida(
    val puntos: Int,
    val correctas: Int,
    val totalPreguntas: Int,
    val rachaMaxima: Int,
    val nivel: Int,
    val subioDeNivel: Boolean,
    val puntosTotales: Int,
    val logrosNuevos: List<String>
)

/**
 * Cierre de partida: guarda el historial, actualiza el progreso del usuario,
 * lo publica en el ranking online y desbloquea los logros que correspondan. Lo usan tanto la partida individual
 * como el duelo local, para no repetir la lógica en cada Activity.
 */
suspend fun finalizarPartida(
    context: Context,
    usuarioId: Long,
    modo: String,
    categoriaId: Int,
    puntos: Int,
    correctas: Int,
    totalPreguntas: Int,
    rachaMaxima: Int
): ResumenPartida {

    val usuarios = UsuarioRepository(context)
    val partidas = PartidaRepository(context)
    val logros = LogroRepository(context)

    val usuario = usuarios.obtenerPorId(usuarioId)
    val nivelAnterior = usuario?.nivel ?: 1
    val puntosTotales = (usuario?.puntosTotales ?: 0) + puntos
    val nivel = LogicaJuego.nivelPara(puntosTotales)

    partidas.guardar(
        Partida(
            usuarioId = usuarioId,
            modo = modo,
            categoriaId = categoriaId,
            puntos = puntos,
            correctas = correctas,
            totalPreguntas = totalPreguntas,
            rachaMaxima = rachaMaxima,
            nivelAlcanzado = nivel
        )
    )

    usuarios.actualizarProgreso(usuarioId, nivel, puntosTotales)
    usuarios.actualizarRachaMaxima(usuarioId, rachaMaxima)

    // RF-10: se publica en segundo plano, así la pantalla de resultado no espera a la red.
    if (usuario != null) {
        RankingRemoto.publicarPartida(
            context,
            JugadorPublicado(usuarioId, usuario.nombreUsuario, puntosTotales, nivel),
            puntos, modo, categoriaId
        )
    }

    val codigos = LogicaJuego.logrosDeLaPartida(
        correctas = correctas,
        totalPreguntas = totalPreguntas,
        rachaMaxima = rachaMaxima,
        partidasJugadas = partidas.contarPartidasJugadas(usuarioId),
        categoriasJugadas = partidas.contarCategoriasJugadas(usuarioId)
    )

    return ResumenPartida(
        puntos = puntos,
        correctas = correctas,
        totalPreguntas = totalPreguntas,
        rachaMaxima = rachaMaxima,
        nivel = nivel,
        subioDeNivel = nivel > nivelAnterior,
        puntosTotales = puntosTotales,
        logrosNuevos = logros.desbloquear(usuarioId, codigos)
    )
}
