package com.calypso.quizzly.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.calypso.quizzly.data.local.entity.LogroUsuario
import com.calypso.quizzly.data.local.entity.Partida
import com.calypso.quizzly.data.local.entity.Usuario
import com.calypso.quizzly.data.repository.LogroRepository
import com.calypso.quizzly.data.repository.PartidaRepository
import com.calypso.quizzly.data.repository.UsuarioRepository
import com.calypso.quizzly.util.Sesion
import kotlinx.coroutines.launch

data class EstadoPerfil(
    val usuario: Usuario,
    val partidasJugadas: Int,
    val logros: List<LogroUsuario>,
    val historial: List<Partida>
)

/** Datos del perfil (RF-12): usuario, logros obtenidos e historial de partidas. */
class PerfilViewModel(app: Application) : AndroidViewModel(app) {

    private val usuarios = UsuarioRepository(app)
    private val partidas = PartidaRepository(app)
    private val logros = LogroRepository(app)

    /** null = no hay un usuario logueado para mostrar. */
    private val _estado = MutableLiveData<EstadoPerfil?>()
    val estado: LiveData<EstadoPerfil?> = _estado

    init {
        viewModelScope.launch {
            val usuarioId = Sesion.usuarioId(app)
            val usuario = if (usuarioId == Sesion.SIN_SESION) null else usuarios.obtenerPorId(usuarioId)

            _estado.value = usuario?.let {
                EstadoPerfil(
                    usuario = it,
                    partidasJugadas = partidas.contarPartidasJugadas(usuarioId),
                    logros = logros.obtenerDeUsuario(usuarioId),
                    historial = partidas.obtenerHistorialReciente(usuarioId)
                )
            }
        }
    }
}
