package com.calypso.quizzly.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.calypso.quizzly.data.local.entity.Usuario
import com.calypso.quizzly.data.remote.DuelosRemotos
import com.calypso.quizzly.data.remote.SesionRemota
import com.calypso.quizzly.data.repository.LogroRepository
import com.calypso.quizzly.data.repository.UsuarioRepository
import com.calypso.quizzly.game.LogicaJuego
import com.calypso.quizzly.util.Sesion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Lo que muestra el menú principal: el usuario con su progreso y los nombres de sus logros. */
data class EstadoHome(val usuario: Usuario, val logros: List<String>)

/** Datos del menú principal (RF-12 parcial) y el aviso de duelos online donde le toca jugar al usuario. */
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val usuarios = UsuarioRepository(app)
    private val logros = LogroRepository(app)

    private val _estado = MutableLiveData<EstadoHome>()
    val estado: LiveData<EstadoHome> = _estado

    /** En cuántos duelos online le toca jugar. Como no hay notificaciones, este es el aviso. */
    private val _turnosPendientes = MutableLiveData(0)
    val turnosPendientes: LiveData<Int> = _turnosPendientes

    init {
        escucharDuelos()
    }

    /** Se llama cada vez que el menú vuelve al frente, para mostrar el progreso después de una partida. */
    fun cargar(usuarioId: Long) {
        viewModelScope.launch {
            val usuario = usuarios.obtenerPorId(usuarioId) ?: return@launch
            val obtenidos = logros.obtenerDeUsuario(usuarioId)
            _estado.value = EstadoHome(
                usuario,
                obtenidos.map { LogicaJuego.nombresDeLogros[it.logroId] ?: it.logroId }
            )
        }
    }

    private fun escucharDuelos() {
        val usuarioId = Sesion.usuarioId(getApplication())
        if (usuarioId == Sesion.SIN_SESION || !SesionRemota.disponible(getApplication())) return

        viewModelScope.launch {
            try {
                val uid = SesionRemota.uid()
                val miJugadorId = SesionRemota.jugadorId(uid, usuarioId)
                DuelosRemotos.escucharMisDuelos(uid).collect { duelos ->
                    _turnosPendientes.value = duelos.count { it.esTurnoDe(miJugadorId) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Sin red el aviso simplemente no aparece: los duelos se ven al entrar a "Duelos online".
            }
        }
    }
}
