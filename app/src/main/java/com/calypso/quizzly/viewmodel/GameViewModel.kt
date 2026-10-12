package com.calypso.quizzly.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.calypso.quizzly.GameActivity
import com.calypso.quizzly.data.remote.DuelosRemotos
import com.calypso.quizzly.game.Categorias
import com.calypso.quizzly.game.Desenlace
import com.calypso.quizzly.game.DueloOnline
import com.calypso.quizzly.game.LogicaJuego
import com.calypso.quizzly.game.ResumenPartida
import com.calypso.quizzly.game.finalizarPartida
import com.calypso.quizzly.util.Sesion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

enum class FaseDeJuego {
    LISTA_PARA_GIRAR,
    GIRANDO,
    CATEGORIA_ELEGIDA,
    GUARDANDO,

    /** Duelo online: el turno ya se jugó pero no se pudo enviar. Se puede reintentar. */
    SIN_CONEXION
}

/** Lo que muestra la pantalla de la ruleta. */
data class EstadoJuego(
    val fase: FaseDeJuego,
    /** Gajo de la ruleta que salió sorteado: vale en GIRANDO y en CATEGORIA_ELEGIDA. */
    val indiceSorteado: Int,
    /** En el duelo, 0 es Jugador 1 y 1 es Jugador 2. */
    val jugadorDeTurno: Int,
    val puntos: List<Int>,
    /** Duelo local: preguntas ya respondidas entre los dos, para mostrar en qué ronda van. */
    val turnosJugados: Int = 0
)

/** La ronda que hay que abrir en la pantalla de preguntas. */
data class RondaPorJugar(val categoriaId: Int, val cantidad: Int)

sealed interface CierreDePartida {
    /** No hay usuario logueado para guardarle la partida. */
    data object SinSesion : CierreDePartida

    /** La partida ya está guardada en la base y hay que mostrar el resultado. */
    data class Guardada(
        val resumen: ResumenPartida,
        val puntos: List<Int>,
        val desenlace: Desenlace
    ) : CierreDePartida

    /** Duelo online: el turno quedó anotado en Firestore y ahora le toca al rival. */
    data class TurnoEnviado(val puntosDelTurno: Int, val duelo: DueloOnline) : CierreDePartida
}

/**
 * Estado de una partida (RF-04 y RF-11): la ruleta, el marcador y los turnos del duelo, y el cierre
 * con [finalizarPartida]. Sobrevive al giro del celular, y con [SavedStateHandle] también a que
 * Android cierre la app en segundo plano mientras se está respondiendo una pregunta.
 *
 * Tiene tres modos: individual, duelo local (los dos jugadores en este celular) y duelo online
 * (acá se juega un solo turno, que se envía a Firestore con [DuelosRemotos]).
 */
class GameViewModel(app: Application, private val guardado: SavedStateHandle) : AndroidViewModel(app) {

    companion object {
        private const val CLAVE_PUNTOS = "partida_puntos"
        private const val CLAVE_CORRECTAS = "partida_correctas"
        private const val CLAVE_TOTALES = "partida_totales"
        private const val CLAVE_RACHAS = "partida_rachas"
        private const val CLAVE_TURNO = "partida_turno"
        private const val CLAVE_TURNOS_JUGADOS = "partida_turnosJugados"
        private const val CLAVE_CATEGORIA = "partida_categoria"
        private const val CLAVE_INDICE = "partida_indiceSorteado"
        private const val CLAVE_ELEGIDA = "partida_categoriaElegida"
    }

    // Los extras del Intent llegan solos al SavedStateHandle, con su misma clave.
    val modo: String = guardado[GameActivity.EXTRA_MODO] ?: GameActivity.MODO_INDIVIDUAL
    val esDuelo = modo == GameActivity.MODO_DUELO
    val esOnline = modo == GameActivity.MODO_DUELO_ONLINE

    /** Los dos duelos muestran el marcador y juegan de a una pregunta por turno. */
    val conMarcador = esDuelo || esOnline

    /** Categoría elegida desde una card del menú (se juega sin ruleta), o null. */
    val categoriaFija: Int? = guardado.get<Int>(GameActivity.EXTRA_CATEGORIA_FIJA)?.takeIf { it != 0 }

    // Duelo online: qué duelo es y qué jugador soy (1 o 2).
    private val codigoDuelo: String = guardado[GameActivity.EXTRA_CODIGO_DUELO] ?: ""
    private val miNumero: Int = guardado[GameActivity.EXTRA_MI_NUMERO] ?: 1

    /** Los nombres de los dos jugadores: los del duelo online, o los que se cargaron en la sala del duelo local. */
    val nombres: List<String> =
        guardado.get<Array<String>>(GameActivity.EXTRA_NOMBRES)?.toList() ?: listOf("Jugador 1", "Jugador 2")

    /** Duelo local: preguntas que responde cada jugador, de a una por turno, elegidas en la sala (P7). */
    val rondasPorJugador: Int = guardado[GameActivity.EXTRA_RONDAS] ?: LogicaJuego.RONDAS_DE_DUELO_POR_DEFECTO
    val nombreDelRival: String get() = nombres[2 - miNumero]

    // Acumulado de la partida, con una posición por jugador (en la individual solo se usa la 0).
    // El duelo online arranca con el marcador que ya tiene el duelo.
    private val puntos: IntArray = guardado[CLAVE_PUNTOS]
        ?: guardado.get<IntArray>(GameActivity.EXTRA_PUNTOS)?.copyOf()
        ?: intArrayOf(0, 0)
    private val correctas: IntArray = guardado[CLAVE_CORRECTAS] ?: intArrayOf(0, 0)
    private val totales: IntArray = guardado[CLAVE_TOTALES] ?: intArrayOf(0, 0)
    private val rachas: IntArray = guardado[CLAVE_RACHAS] ?: intArrayOf(0, 0)
    private var jugadorDeTurno: Int = guardado[CLAVE_TURNO] ?: if (esOnline) miNumero - 1 else 0

    /** El turno online ya jugado que falta enviar (o que se está enviando). */
    private var turnoPorEnviar: ResultadoRonda? = null
    private var turnosJugados: Int = guardado[CLAVE_TURNOS_JUGADOS] ?: 0

    /** En el duelo local, si ya se jugó algún turno: salir haría perder el marcador. */
    val hayTurnosJugados: Boolean get() = turnosJugados > 0
    private var ultimaCategoriaId: Int = guardado[CLAVE_CATEGORIA] ?: (categoriaFija ?: 1)
    private var indiceSorteado: Int = guardado[CLAVE_INDICE] ?: 0

    // Si Android cerró la app en medio del giro, la categoría ya estaba sorteada: se da por elegida.
    private var fase =
        if (guardado.get<Boolean>(CLAVE_ELEGIDA) == true) FaseDeJuego.CATEGORIA_ELEGIDA
        else FaseDeJuego.LISTA_PARA_GIRAR

    private val _estado = MutableLiveData(estadoActual())
    val estado: LiveData<EstadoJuego> = _estado

    private val _cierre = MutableLiveData<CierreDePartida>()
    val cierre: LiveData<CierreDePartida> = _cierre

    private fun estadoActual() = EstadoJuego(fase, indiceSorteado, jugadorDeTurno, puntos.toList(), turnosJugados)

    private fun publicar() {
        guardado[CLAVE_PUNTOS] = puntos
        guardado[CLAVE_CORRECTAS] = correctas
        guardado[CLAVE_TOTALES] = totales
        guardado[CLAVE_RACHAS] = rachas
        guardado[CLAVE_TURNO] = jugadorDeTurno
        guardado[CLAVE_TURNOS_JUGADOS] = turnosJugados
        guardado[CLAVE_CATEGORIA] = ultimaCategoriaId
        guardado[CLAVE_INDICE] = indiceSorteado
        guardado[CLAVE_ELEGIDA] = fase == FaseDeJuego.GIRANDO || fase == FaseDeJuego.CATEGORIA_ELEGIDA
        _estado.value = estadoActual()
    }

    /** Sortea la categoría y devuelve el gajo donde tiene que frenar la ruleta; null si no se puede girar ahora. */
    fun girar(): Int? {
        if (fase != FaseDeJuego.LISTA_PARA_GIRAR) return null
        indiceSorteado = Random.nextInt(Categorias.todas.size)
        fase = FaseDeJuego.GIRANDO
        publicar()
        return indiceSorteado
    }

    fun ruletaDetenida() {
        if (fase != FaseDeJuego.GIRANDO) return
        fase = FaseDeJuego.CATEGORIA_ELEGIDA
        publicar()
    }

    /** La ronda que toca abrir: la categoría fija del menú o la que salió en la ruleta. */
    fun rondaPorJugar(): RondaPorJugar {
        ultimaCategoriaId = categoriaFija ?: Categorias.porIndice(indiceSorteado).id
        publicar()
        return RondaPorJugar(
            categoriaId = ultimaCategoriaId,
            cantidad = if (conMarcador) 1 else LogicaJuego.PREGUNTAS_POR_PARTIDA
        )
    }

    /**
     * Suma el resultado de la ronda y decide cómo sigue la partida.
     * Devuelve true si ahora le toca al otro jugador en este mismo celular; si no, la partida
     * terminó y se está guardando (o, en el duelo online, el turno se está enviando).
     */
    fun rondaTerminada(resultado: ResultadoRonda): Boolean {
        if (esOnline) {
            enviarTurno(resultado)
            return false
        }

        val jugador = if (esDuelo) jugadorDeTurno else 0

        puntos[jugador] += resultado.puntos
        correctas[jugador] += resultado.correctas
        totales[jugador] += resultado.total
        rachas[jugador] = maxOf(rachas[jugador], resultado.rachaMaxima)
        turnosJugados++

        if (!esDuelo || turnosJugados >= rondasPorJugador * 2) {
            cerrarPartida()
            return false
        }

        jugadorDeTurno = 1 - jugadorDeTurno
        fase = FaseDeJuego.LISTA_PARA_GIRAR
        publicar()
        return true
    }

    /**
     * El jugador salió de la ronda con "atrás". En este celular no se guarda nada ni pierde el turno;
     * en el duelo online cuenta como pregunta errada, para que no sirva para cambiar de pregunta.
     */
    fun rondaCancelada() {
        if (esOnline) {
            enviarTurno(ResultadoRonda(puntos = 0, correctas = 0, total = 1, rachaMaxima = 0))
            return
        }
        fase = FaseDeJuego.LISTA_PARA_GIRAR
        publicar()
    }

    /** Anota el turno en el marcador de esta pantalla y lo manda a Firestore. */
    private fun enviarTurno(resultado: ResultadoRonda) {
        if (turnoPorEnviar != null) return
        turnoPorEnviar = resultado
        puntos[miNumero - 1] += resultado.puntos
        mandarTurnoPendiente()
    }

    /** Duelo online: vuelve a intentar el envío del turno ya jugado, después de un corte de red. */
    fun reintentarEnvio() {
        if (fase == FaseDeJuego.SIN_CONEXION) mandarTurnoPendiente()
    }

    private fun mandarTurnoPendiente() {
        val turno = turnoPorEnviar ?: return
        fase = FaseDeJuego.GUARDANDO
        publicar()

        viewModelScope.launch {
            try {
                val duelo = DuelosRemotos.jugarTurno(
                    codigo = codigoDuelo,
                    numero = miNumero,
                    puntos = turno.puntos,
                    acerto = turno.correctas > 0,
                    categoriaId = ultimaCategoriaId
                )
                _cierre.value = CierreDePartida.TurnoEnviado(turno.puntos, duelo)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fase = FaseDeJuego.SIN_CONEXION
                publicar()
            }
        }
    }

    private fun cerrarPartida() {
        if (fase == FaseDeJuego.GUARDANDO) return
        fase = FaseDeJuego.GUARDANDO
        publicar()

        val usuarioId = Sesion.usuarioId(getApplication())
        if (usuarioId == Sesion.SIN_SESION) {
            _cierre.value = CierreDePartida.SinSesion
            return
        }

        viewModelScope.launch {
            // No se corta a la mitad aunque el jugador salga de la pantalla mientras se guarda.
            // En el duelo local se guarda la partida del usuario logueado (Jugador 1).
            val resumen = withContext(NonCancellable) {
                finalizarPartida(
                    context = getApplication(),
                    usuarioId = usuarioId,
                    modo = modo,
                    categoriaId = ultimaCategoriaId,
                    puntos = puntos[0],
                    correctas = correctas[0],
                    totalPreguntas = totales[0],
                    rachaMaxima = rachas[0]
                )
            }

            val desenlace = when {
                !esDuelo -> LogicaJuego.desenlaceIndividual(resumen.correctas, resumen.totalPreguntas)
                // En el duelo siempre festeja alguien, salvo que empaten.
                puntos[0] == puntos[1] -> Desenlace.EMPATE
                else -> Desenlace.GANADA
            }
            _cierre.value = CierreDePartida.Guardada(resumen, puntos.toList(), desenlace)
        }
    }
}
