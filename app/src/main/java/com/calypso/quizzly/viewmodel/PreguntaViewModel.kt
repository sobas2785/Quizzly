package com.calypso.quizzly.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.calypso.quizzly.data.local.entity.Pregunta
import com.calypso.quizzly.data.repository.PreguntaRepository
import com.calypso.quizzly.data.repository.UsuarioRepository
import com.calypso.quizzly.game.LogicaJuego
import com.calypso.quizzly.util.Sesion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/** Todo lo que muestra la pantalla de preguntas en un momento dado. */
data class EstadoPregunta(
    /** null mientras se carga la primera pregunta. */
    val pregunta: Pregunta? = null,
    val numero: Int = 0,
    val cantidad: Int = LogicaJuego.PREGUNTAS_POR_PARTIDA,
    val segundosRestantes: Int = LogicaJuego.SEGUNDOS_POR_PREGUNTA,
    val racha: Int = 0,
    val puntos: Int = 0,
    val respondida: Boolean = false,
    /** Letra que tocó el jugador; null si todavía no respondió o si se le acabó el tiempo. */
    val letraElegida: String? = null,
    /** Deja de ser null cuando la ronda terminó y hay que devolver el resultado. */
    val resultado: ResultadoRonda? = null
) {
    val tiempoAgotado get() = respondida && letraElegida == null
}

/** Lo que una ronda le devuelve a quien la lanzó (la partida individual o un turno del duelo). */
data class ResultadoRonda(val puntos: Int, val correctas: Int, val total: Int, val rachaMaxima: Int)

/** Qué pasó con la respuesta, para que la pantalla suene y vibre. */
enum class EfectoRespuesta { ACIERTO, RACHA, ERROR, TIEMPO_AGOTADO }

/**
 * Ronda de preguntas de una categoría (RF-05, RF-06, RF-07, RF-14): pregunta actual, temporizador,
 * racha y puntaje. Vive más que la Activity, así que girar el celular no reinicia la ronda.
 */
class PreguntaViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        /** Cuánto se ve la respuesta correcta antes de pasar a la pregunta siguiente. */
        private const val MS_DE_FEEDBACK = 900L
    }

    private val preguntas = PreguntaRepository(app)
    private val usuarios = UsuarioRepository(app)

    private val _estado = MutableLiveData(EstadoPregunta())
    val estado: LiveData<EstadoPregunta> = _estado

    // Sin búfer para pantallas ausentes: si la app está en segundo plano, el efecto se pierde
    // en vez de acumularse y sonar todo junto al volver.
    private val _efectos = MutableSharedFlow<EfectoRespuesta>(extraBufferCapacity = 1)
    val efectos: SharedFlow<EfectoRespuesta> = _efectos

    private var iniciada = false
    private var categoriaId = 1
    private var dificultad = "Facil"
    private var correctas = 0
    private var rachaMaxima = 0
    private val yaUsadas = mutableSetOf<Long>()
    private var temporizador: Job? = null

    private val actual get() = _estado.value ?: EstadoPregunta()

    /** Arranca la ronda. La Activity lo llama en cada onCreate, pero solo la primera vez hace algo. */
    fun iniciar(categoriaId: Int, cantidad: Int) {
        if (iniciada) return
        iniciada = true
        this.categoriaId = categoriaId
        _estado.value = EstadoPregunta(cantidad = cantidad)

        viewModelScope.launch {
            // RF-07: la dificultad depende del nivel del usuario logueado.
            val nivel = usuarios.obtenerPorId(Sesion.usuarioId(getApplication()))?.nivel ?: 1
            dificultad = LogicaJuego.dificultadPara(nivel)
            siguientePregunta()
        }
    }

    private suspend fun siguientePregunta() {
        val pregunta =
            if (actual.numero < actual.cantidad) preguntas.siguientePregunta(categoriaId, dificultad, yaUsadas)
            else null

        if (pregunta == null) {
            // Se completó la ronda, o la categoría se quedó sin preguntas: se cierra con lo jugado.
            _estado.value = actual.let {
                it.copy(resultado = ResultadoRonda(it.puntos, correctas, it.numero, rachaMaxima))
            }
            return
        }

        yaUsadas += pregunta.id
        _estado.value = actual.copy(
            pregunta = pregunta.conOpcionesMezcladas(),
            numero = actual.numero + 1,
            segundosRestantes = LogicaJuego.SEGUNDOS_POR_PREGUNTA,
            respondida = false,
            letraElegida = null
        )
        arrancarTemporizador()
    }

    /** RF-05: la misma pregunta con las opciones en otro orden y la respuesta apuntando a la letra nueva. */
    private fun Pregunta.conOpcionesMezcladas(): Pregunta {
        val opciones = LogicaJuego.mezclarOpciones(listOf(opcionA, opcionB, opcionC, opcionD), respuesta)
        return copy(
            opcionA = opciones.textos[0],
            opcionB = opciones.textos[1],
            opcionC = opciones.textos[2],
            opcionD = opciones.textos[3],
            respuesta = opciones.correcta
        )
    }

    private fun arrancarTemporizador() {
        temporizador?.cancel()
        temporizador = viewModelScope.launch {
            for (segundos in LogicaJuego.SEGUNDOS_POR_PREGUNTA - 1 downTo 0) {
                delay(1000L)
                _estado.value = actual.copy(segundosRestantes = segundos)
            }
            responder(null)
        }
    }

    /** [letra] null = se acabó el tiempo: cuenta como incorrecta y corta la racha. */
    fun responder(letra: String?) {
        val estado = actual
        val pregunta = estado.pregunta ?: return
        if (estado.respondida || estado.resultado != null) return
        temporizador?.cancel()

        val acerto = letra == pregunta.respuesta
        val racha = if (acerto) estado.racha + 1 else 0
        var puntos = estado.puntos

        if (acerto) {
            correctas++
            rachaMaxima = maxOf(rachaMaxima, racha)
            puntos += LogicaJuego.puntosDeRespuesta(estado.segundosRestantes, racha)
        }

        _estado.value = estado.copy(respondida = true, letraElegida = letra, racha = racha, puntos = puntos)
        _efectos.tryEmit(
            when {
                letra == null -> EfectoRespuesta.TIEMPO_AGOTADO
                !acerto -> EfectoRespuesta.ERROR
                LogicaJuego.esRachaDestacada(racha) -> EfectoRespuesta.RACHA
                else -> EfectoRespuesta.ACIERTO
            }
        )

        viewModelScope.launch {
            delay(MS_DE_FEEDBACK)
            siguientePregunta()
        }
    }
}
