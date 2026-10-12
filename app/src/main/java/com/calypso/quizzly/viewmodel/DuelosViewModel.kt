package com.calypso.quizzly.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.calypso.quizzly.GameActivity
import com.calypso.quizzly.data.remote.DuelosRemotos
import com.calypso.quizzly.data.remote.DuelosRemotos.ResultadoUnirse
import com.calypso.quizzly.data.remote.SesionRemota
import com.calypso.quizzly.data.repository.UsuarioRepository
import com.calypso.quizzly.game.Desenlace
import com.calypso.quizzly.game.DueloOnline
import com.calypso.quizzly.game.EstadoDuelo
import com.calypso.quizzly.game.ReglasDueloOnline
import com.calypso.quizzly.game.ResumenPartida
import com.calypso.quizzly.game.finalizarPartida
import com.calypso.quizzly.util.Sesion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface EstadoDuelos {
    data object Cargando : EstadoDuelos

    /** No se puede jugar online ahora: falta configurar Firebase, no hay sesión o no hay red. */
    data class NoDisponible(val motivo: String, val sePuedeReintentar: Boolean) : EstadoDuelos

    data class Lista(val duelos: List<DueloOnline>, val miJugadorId: String) : EstadoDuelos
}

sealed interface EfectoDuelos {
    data class Aviso(val mensaje: String) : EfectoDuelos

    /** Hay un duelo nuevo esperando rival: hay que mostrar el código para compartirlo. */
    data class Creado(val duelo: DueloOnline) : EfectoDuelos

    /** Un duelo terminó y ya se guardó como partida: hay que mostrar cómo salió. */
    data class Cerrado(val duelo: DueloOnline, val desenlace: Desenlace, val resumen: ResumenPartida) : EfectoDuelos
}

/**
 * Los duelos online del usuario: la lista en vivo, crear uno, unirse con un código y cancelar.
 * Cuando ve que un duelo terminó, lo guarda una sola vez como partida del usuario (historial,
 * puntos, logros y ranking), igual que una partida jugada en este celular.
 */
class DuelosViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val PREFS = "quizzly_duelos"
    }

    private val usuarios = UsuarioRepository(app)
    private val usuarioId = Sesion.usuarioId(app)
    private var nombre = ""

    private val _estado = MutableLiveData<EstadoDuelos>(EstadoDuelos.Cargando)
    val estado: LiveData<EstadoDuelos> = _estado

    private val _ocupado = MutableLiveData(false)
    val ocupado: LiveData<Boolean> = _ocupado

    private val _efectos = Channel<EfectoDuelos>(Channel.BUFFERED)
    val efectos = _efectos.receiveAsFlow()

    private var escucha: Job? = null

    init {
        conectar()
    }

    /** Abre la sesión de Firebase y empieza a escuchar los duelos. Se puede volver a llamar si falló. */
    fun conectar() {
        if (!SesionRemota.disponible(getApplication())) {
            _estado.value = EstadoDuelos.NoDisponible("El duelo online no está configurado en esta versión de la app.", false)
            return
        }

        escucha?.cancel()
        _estado.value = EstadoDuelos.Cargando

        escucha = viewModelScope.launch {
            try {
                val usuario = usuarios.obtenerPorId(usuarioId)
                if (usuario == null) {
                    _estado.value = EstadoDuelos.NoDisponible("Iniciá sesión para jugar duelos online.", false)
                    return@launch
                }
                nombre = usuario.nombreUsuario

                val uid = SesionRemota.uid()
                val miJugadorId = SesionRemota.jugadorId(uid, usuarioId)

                DuelosRemotos.escucharMisDuelos(uid).collect { delCelular ->
                    // El uid es del celular: se dejan solo los duelos de la cuenta que está logueada.
                    val mios = delCelular.filter { it.numeroDe(miJugadorId) != null }
                    _estado.value = EstadoDuelos.Lista(ReglasDueloOnline.ordenar(mios, miJugadorId), miJugadorId)
                    guardarLosTerminados(mios, miJugadorId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.value = EstadoDuelos.NoDisponible("No se pudo conectar. Revisá tu conexión a internet.", true)
            }
        }
    }

    fun crear() = hacer {
        _efectos.send(EfectoDuelos.Creado(DuelosRemotos.crear(usuarioId, nombre)))
    }

    fun unirse(textoDelCodigo: String) {
        val codigo = ReglasDueloOnline.normalizarCodigo(textoDelCodigo)
        if (codigo == null) {
            avisar("El código tiene ${ReglasDueloOnline.LARGO_DEL_CODIGO} letras o números")
            return
        }

        hacer {
            avisar(
                when (val resultado = DuelosRemotos.unirse(codigo, usuarioId, nombre)) {
                    is ResultadoUnirse.Unido -> "¡Te uniste! Empieza ${resultado.duelo.nombre1}"
                    ResultadoUnirse.NoExiste -> "No hay ningún duelo con ese código"
                    ResultadoUnirse.YaEmpezo -> "Ese duelo ya tiene dos jugadores"
                    ResultadoUnirse.EsPropio -> "Ese duelo lo creaste en este celular: pasale el código a otra persona"
                }
            )
        }
    }

    fun cancelar(duelo: DueloOnline) = hacer {
        DuelosRemotos.cancelar(duelo.codigo)
    }

    /** Corre una acción contra Firestore, de a una por vez, y avisa si falla (casi siempre, por falta de red). */
    private fun hacer(accion: suspend () -> Unit) {
        if (_ocupado.value == true) return
        _ocupado.value = true

        viewModelScope.launch {
            try {
                accion()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                avisar("No se pudo completar. Revisá tu conexión a internet.")
            } finally {
                _ocupado.value = false
            }
        }
    }

    private fun avisar(mensaje: String) {
        _efectos.trySend(EfectoDuelos.Aviso(mensaje))
    }

    /**
     * Cada duelo terminado se guarda una sola vez como partida del usuario. Los que ya se guardaron
     * se recuerdan en el celular, porque cada jugador registra su parte en el suyo.
     */
    private suspend fun guardarLosTerminados(duelos: List<DueloOnline>, miJugadorId: String) {
        val prefs = getApplication<Application>().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val clave = "guardados_$usuarioId"
        val guardados = prefs.getStringSet(clave, emptySet()).orEmpty()

        duelos.filter { it.estado == EstadoDuelo.TERMINADO && it.codigo !in guardados }.forEach { duelo ->
            val numero = duelo.numeroDe(miJugadorId) ?: return@forEach
            val desenlace = duelo.desenlacePara(miJugadorId) ?: return@forEach
            val correctas = duelo.correctasDe(numero)

            // Se anota antes de guardar, para que otra actualización de la lista no lo guarde dos veces.
            prefs.edit().putStringSet(clave, prefs.getStringSet(clave, emptySet()).orEmpty() + duelo.codigo).apply()

            val resumen = withContext(NonCancellable) {
                finalizarPartida(
                    context = getApplication(),
                    usuarioId = usuarioId,
                    modo = GameActivity.MODO_DUELO_ONLINE,
                    // Como en el duelo local, la partida queda con la última categoría jugada.
                    categoriaId = duelo.ultimaCategoriaId.takeIf { it in 1..6 } ?: 1,
                    puntos = duelo.puntosDe(numero),
                    correctas = correctas,
                    totalPreguntas = ReglasDueloOnline.TURNOS_POR_JUGADOR,
                    // Cada turno es una sola pregunta: la racha de una ronda nunca pasa de 1.
                    rachaMaxima = if (correctas > 0) 1 else 0
                )
            }
            _efectos.send(EfectoDuelos.Cerrado(duelo, desenlace, resumen))
        }
    }
}
