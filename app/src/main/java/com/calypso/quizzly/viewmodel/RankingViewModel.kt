package com.calypso.quizzly.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.calypso.quizzly.data.remote.JugadorPublicado
import com.calypso.quizzly.data.remote.RankingRemoto
import com.calypso.quizzly.data.repository.PartidaRepository
import com.calypso.quizzly.data.repository.UsuarioRepository
import com.calypso.quizzly.game.FilaRanking
import com.calypso.quizzly.game.Periodo
import com.calypso.quizzly.game.PuntajeDePartida
import com.calypso.quizzly.game.Ranking
import com.calypso.quizzly.util.Sesion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

sealed interface EstadoRanking {
    data object Cargando : EstadoRanking
    data object Vacio : EstadoRanking
    data object Error : EstadoRanking

    /** [miJugadorId] es la fila del usuario logueado, para resaltarla. */
    data class Tabla(val filas: List<FilaRanking>, val miJugadorId: String?) : EstadoRanking
}

/**
 * Ranking de jugadores (RF-10) con filtros por día, semana, mes y global.
 * Con Firebase configurado muestra a todos los jugadores; si no, a los de este dispositivo.
 */
class RankingViewModel(app: Application) : AndroidViewModel(app) {

    private val usuarios = UsuarioRepository(app)
    private val partidas = PartidaRepository(app)
    private val usuarioId = Sesion.usuarioId(app)

    val online = RankingRemoto.disponible(app)

    private val _estado = MutableLiveData<EstadoRanking>()
    val estado: LiveData<EstadoRanking> = _estado

    var periodo = Periodo.GLOBAL
        private set

    private var consulta: Job? = null

    init {
        // Así el jugador aparece en el global aunque sus partidas sean de antes de Firebase.
        if (online) {
            viewModelScope.launch {
                usuarios.obtenerPorId(usuarioId)?.let {
                    RankingRemoto.sincronizarJugador(app, JugadorPublicado(it.id, it.nombreUsuario, it.puntosTotales, it.nivel))
                }
            }
        }
        consultar()
    }

    /** Cambia el filtro. Devuelve false si ese período ya era el que se estaba mostrando. */
    fun elegir(nuevo: Periodo): Boolean {
        if (nuevo == periodo) return false
        periodo = nuevo
        consultar()
        return true
    }

    private fun consultar() {
        consulta?.cancel()
        _estado.value = EstadoRanking.Cargando

        consulta = viewModelScope.launch {
            _estado.value = try {
                val filas = if (online) RankingRemoto.obtenerRanking(periodo) else rankingLocal(periodo)
                if (filas.isEmpty()) EstadoRanking.Vacio else EstadoRanking.Tabla(filas, miJugadorId())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                EstadoRanking.Error
            }
        }
    }

    private fun miJugadorId(): String? =
        if (online) RankingRemoto.miJugadorId(getApplication(), usuarioId) else usuarioId.toString()

    /** Sin Firebase: los usuarios de este dispositivo, con las mismas reglas de período. */
    private suspend fun rankingLocal(periodo: Periodo): List<FilaRanking> {
        val todos = usuarios.obtenerRankingGlobal()
        val desde = Ranking.inicioDelPeriodo(periodo, ZonedDateTime.now())
            ?: return Ranking.ordenar(todos.map { FilaRanking(it.id.toString(), it.nombreUsuario, it.puntosTotales) })

        val nombres = todos.associate { it.id to it.nombreUsuario }
        return Ranking.sumarPorJugador(
            partidas.obtenerDesde(desde).map {
                PuntajeDePartida(it.usuarioId.toString(), nombres[it.usuarioId] ?: "?", it.puntos)
            }
        )
    }
}
