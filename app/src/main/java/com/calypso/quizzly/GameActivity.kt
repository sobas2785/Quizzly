package com.calypso.quizzly

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.calypso.quizzly.game.Categorias
import com.calypso.quizzly.util.Sonidos
import com.calypso.quizzly.util.Vibracion
import com.calypso.quizzly.viewmodel.CierreDePartida
import com.calypso.quizzly.viewmodel.EstadoJuego
import com.calypso.quizzly.viewmodel.FaseDeJuego
import com.calypso.quizzly.viewmodel.GameViewModel
import com.calypso.quizzly.viewmodel.ResultadoRonda
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.random.Random

/**
 * Ruleta de categorías (RF-04). Maneja los dos modos de juego:
 * individual (una ronda de 5 preguntas) y duelo local por turnos (RF-11).
 * El estado de la partida está en [GameViewModel]; acá quedan la animación, los sonidos y la navegación.
 */
class GameActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MODO = "modo"
        const val EXTRA_CATEGORIA_FIJA = "categoriaFija"

        const val MODO_INDIVIDUAL = "individual"
        const val MODO_DUELO = "duelo_local"
        const val MODO_DUELO_ONLINE = "duelo_online"

        /** Los nombres de los dos jugadores, en los dos duelos. */
        const val EXTRA_NOMBRES = "nombres"

        /** Solo para el duelo local: preguntas por jugador, elegidas en la sala de duelo. */
        const val EXTRA_RONDAS = "rondas"

        // Solo para el duelo online: qué duelo es, qué jugador soy (1 o 2) y los puntos de los dos.
        const val EXTRA_CODIGO_DUELO = "codigoDuelo"
        const val EXTRA_MI_NUMERO = "miNumero"
        const val EXTRA_PUNTOS = "puntosDelDuelo"

        /** Lo que devuelve un turno online ya enviado: los puntos que sumó. */
        const val RESULTADO_PUNTOS_DEL_TURNO = "puntosDelTurno"
    }

    private val viewModel: GameViewModel by viewModels()

    private lateinit var quizWheel: QuizWheelView
    private lateinit var btnSpin: MaterialButton
    private lateinit var tvSelectedCategory: TextView
    private lateinit var cardSelectedCategory: MaterialCardView
    private lateinit var tvTurn: TextView
    private lateinit var tvTurnHint: TextView
    private lateinit var tvScore1: TextView
    private lateinit var tvScore2: TextView

    private var currentRotation = 0f
    private var animacion: ValueAnimator? = null
    private var dialogoSalida: AlertDialog? = null

    private val rondaDePreguntas = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { resultado ->
        val datos = resultado.data

        if (resultado.resultCode != Activity.RESULT_OK || datos == null) {
            // El jugador salió de la ronda con "atrás". En la individual se sale sin guardar; en los
            // duelos decide el ViewModel (el local no pierde el turno, el online lo cuenta como errado).
            if (viewModel.conMarcador) viewModel.rondaCancelada() else finish()
            return@registerForActivityResult
        }

        val cambioDeTurno = viewModel.rondaTerminada(
            ResultadoRonda(
                puntos = datos.getIntExtra(PreguntaActivity.RESULTADO_PUNTOS, 0),
                correctas = datos.getIntExtra(PreguntaActivity.RESULTADO_CORRECTAS, 0),
                total = datos.getIntExtra(PreguntaActivity.RESULTADO_TOTAL, 0),
                rachaMaxima = datos.getIntExtra(PreguntaActivity.RESULTADO_RACHA_MAXIMA, 0)
            )
        )
        if (cambioDeTurno) Sonidos.cambioDeTurno(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        quizWheel = findViewById(R.id.quizWheel)
        btnSpin = findViewById(R.id.btnSpin)
        tvSelectedCategory = findViewById(R.id.tvSelectedCategory)
        cardSelectedCategory = findViewById(R.id.cardSelectedCategory)
        tvTurn = findViewById(R.id.tvTurn)
        tvTurnHint = findViewById(R.id.tvTurnHint)
        tvScore1 = findViewById(R.id.tvScore1)
        tvScore2 = findViewById(R.id.tvScore2)

        tvTurnHint.text = "Girá la ruleta para descubrir tu categoría"
        if (viewModel.conMarcador) {
            findViewById<TextView>(R.id.tvPlayer1).text = viewModel.nombres[0]
            findViewById<TextView>(R.id.tvPlayer2).text = viewModel.nombres[1]
        }
        when {
            viewModel.esOnline -> tvTurn.text = "Tu turno contra ${viewModel.nombreDelRival}"

            !viewModel.esDuelo -> {
                findViewById<MaterialCardView>(R.id.cardScore).visibility = View.GONE
                tvTurn.text = "Partida individual"
            }
        }

        btnSpin.setOnClickListener {
            when (viewModel.estado.value?.fase) {
                FaseDeJuego.LISTA_PARA_GIRAR -> girarRuleta()
                FaseDeJuego.CATEGORIA_ELEGIDA -> {
                    Sonidos.toque(this)
                    abrirRonda()
                }
                FaseDeJuego.SIN_CONEXION -> {
                    Sonidos.toque(this)
                    viewModel.reintentarEnvio()
                }
                else -> Unit
            }
        }

        // Si la pantalla se recreó (por ejemplo, al girar el celular) con una categoría ya sorteada,
        // la ruleta arranca apuntando a esa categoría, sin repetir la animación.
        viewModel.estado.value?.let {
            if (it.fase == FaseDeJuego.GIRANDO || it.fase == FaseDeJuego.CATEGORIA_ELEGIDA) {
                currentRotation = quizWheel.getRotationForCategory(it.indiceSorteado)
                quizWheel.rotationAngle = currentRotation
                viewModel.ruletaDetenida()
            }
        }

        viewModel.estado.observe(this, ::mostrar)
        viewModel.cierre.observe(this, ::cerrar)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = alApretarAtras()
        })

        // Si el usuario entró por una card de categoría, no hay ruleta: se juega esa categoría.
        // Solo la primera vez: si la pantalla se recrea, la ronda ya está abierta.
        if (savedInstanceState == null && viewModel.categoriaFija != null) abrirRonda()
    }

    override fun onDestroy() {
        // Una animación a medias no tiene que avisarle nada al ViewModel de la pantalla nueva.
        animacion?.removeAllListeners()
        animacion?.cancel()
        dialogoSalida?.dismiss()
        super.onDestroy()
    }

    /** "Atrás" en la ruleta: pide confirmación solo si al salir se perdería algo. */
    private fun alApretarAtras() {
        val fase = viewModel.estado.value?.fase
        when {
            // Salir mientras se guarda la partida o se envía el turno podría cortar el envío.
            fase == FaseDeJuego.GUARDANDO ->
                Toast.makeText(this, "Esperá un segundo: se está guardando", Toast.LENGTH_SHORT).show()

            fase == FaseDeJuego.SIN_CONEXION -> confirmarSalida(
                "¿Salir sin enviar tu turno?",
                "Tu respuesta todavía no llegó al servidor. Si salís ahora, no se registra."
            )

            viewModel.esDuelo && viewModel.hayTurnosJugados -> confirmarSalida(
                "¿Abandonar el duelo?",
                "Se pierde el marcador y la partida no se guarda."
            )

            else -> finish()
        }
    }

    private fun confirmarSalida(titulo: String, mensaje: String) {
        if (dialogoSalida?.isShowing == true) return
        dialogoSalida = MaterialAlertDialogBuilder(this)
            .setTitle(titulo)
            .setMessage(mensaje)
            .setPositiveButton("Salir") { _, _ -> finish() }
            .setNegativeButton("Seguir jugando", null)
            .show()
    }

    private fun mostrar(estado: EstadoJuego) {
        if (viewModel.conMarcador) {
            tvScore1.text = estado.puntos[0].toString()
            tvScore2.text = estado.puntos[1].toString()
        }
        if (viewModel.esDuelo) {
            tvTurn.text = "Turno de ${viewModel.nombres[estado.jugadorDeTurno]}"
            val pregunta = minOf(estado.turnosJugados / 2 + 1, viewModel.rondasPorJugador)
            tvTurnHint.text = "Pregunta $pregunta de ${viewModel.rondasPorJugador} · girá la ruleta"
        }

        when (estado.fase) {
            FaseDeJuego.LISTA_PARA_GIRAR -> {
                btnSpin.text = "Girar ruleta"
                btnSpin.isEnabled = true
                mostrarCartel("Categoría: ?", null, Color.parseColor("#6D28D9"), Color.WHITE, Color.parseColor("#DDD6FE"))
            }

            FaseDeJuego.GIRANDO -> {
                btnSpin.isEnabled = false
                mostrarCartel("Girando...", null, Color.parseColor("#6B7280"), Color.WHITE, Color.parseColor("#E5E7EB"))
            }

            FaseDeJuego.CATEGORIA_ELEGIDA -> {
                val categoria = Categorias.porIndice(estado.indiceSorteado)
                btnSpin.text = "Responder →"
                btnSpin.isEnabled = true
                mostrarCartel(
                    "Categoría: ${categoria.nombre}",
                    categoria.ilustracion,
                    categoria.colorTexto,
                    categoria.colorFondoClaro,
                    categoria.colorTexto
                )
            }

            FaseDeJuego.GUARDANDO -> {
                btnSpin.isEnabled = false
                if (viewModel.esOnline) {
                    mostrarCartel("Enviando tu turno...", null, Color.parseColor("#6B7280"), Color.WHITE, Color.parseColor("#E5E7EB"))
                }
            }

            FaseDeJuego.SIN_CONEXION -> {
                btnSpin.text = "Reintentar envío"
                btnSpin.isEnabled = true
                mostrarCartel("Sin conexión: tu turno no se envió", null, Color.parseColor("#B91C1C"), Color.parseColor("#FEF2F2"), Color.parseColor("#FECACA"))
            }
        }
    }

    /** El cartel de abajo de la ruleta, con el personaje de la categoría a la izquierda si hay uno. */
    private fun mostrarCartel(texto: String, @DrawableRes ilustracion: Int?, colorTexto: Int, fondo: Int, borde: Int) {
        val densidad = resources.displayMetrics.density
        val personaje = ilustracion?.let { ContextCompat.getDrawable(this, it) }?.apply {
            val lado = (32 * densidad).toInt()
            setBounds(0, 0, lado, lado)
        }

        tvSelectedCategory.text = texto
        tvSelectedCategory.setTextColor(colorTexto)
        tvSelectedCategory.compoundDrawablePadding = (8 * densidad).toInt()
        tvSelectedCategory.setCompoundDrawablesRelative(personaje, null, null, null)
        cardSelectedCategory.setCardBackgroundColor(fondo)
        cardSelectedCategory.strokeColor = borde
    }

    private fun girarRuleta() {
        val indice = viewModel.girar() ?: return
        Vibracion.toque(this)
        Sonidos.girarRuleta(this)

        val extraTurns = Random.nextInt(4, 7)
        val categoryRotation = quizWheel.getRotationForCategory(indice)
        val normalizedCurrent = currentRotation % 360f
        val finalRotation = currentRotation + (extraTurns * 360f) + categoryRotation - normalizedCurrent

        animacion = ValueAnimator.ofFloat(currentRotation, finalRotation).apply {
            duration = 3500L
            interpolator = DecelerateInterpolator(2.5f)

            addUpdateListener { quizWheel.rotationAngle = it.animatedValue as Float }

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    currentRotation = finalRotation
                    animacion = null
                    Vibracion.categoriaElegida(this@GameActivity)
                    Sonidos.ruletaDetenida(this@GameActivity)
                    viewModel.ruletaDetenida()
                }
            })

            start()
        }
    }

    private fun abrirRonda() {
        val ronda = viewModel.rondaPorJugar()
        val etiqueta = when {
            viewModel.esOnline -> "Duelo contra ${viewModel.nombreDelRival}"
            viewModel.esDuelo -> "Turno de ${viewModel.nombres[viewModel.estado.value?.jugadorDeTurno ?: 0]}"
            else -> "Partida individual"
        }

        rondaDePreguntas.launch(
            Intent(this, PreguntaActivity::class.java)
                .putExtra(PreguntaActivity.EXTRA_CATEGORIA_ID, ronda.categoriaId)
                .putExtra(PreguntaActivity.EXTRA_CANTIDAD, ronda.cantidad)
                .putExtra(PreguntaActivity.EXTRA_ETIQUETA_JUGADOR, etiqueta)
                .putExtra(PreguntaActivity.EXTRA_MODO, viewModel.modo)
        )
    }

    /** La partida ya está guardada: se arma el texto del resultado y se pasa a esa pantalla. */
    private fun cerrar(cierre: CierreDePartida) {
        if (isFinishing) return

        if (cierre is CierreDePartida.TurnoEnviado) {
            // Duelo online: se vuelve a "Mis duelos", que se actualiza sola con el turno ya anotado.
            setResult(Activity.RESULT_OK, Intent().putExtra(RESULTADO_PUNTOS_DEL_TURNO, cierre.puntosDelTurno))
            finish()
            return
        }

        if (cierre !is CierreDePartida.Guardada) {
            finish()
            return
        }

        val resumen = cierre.resumen
        val puntos = cierre.puntos
        val titulo: String
        val subtitulo: String

        if (viewModel.esDuelo) {
            val (nombre1, nombre2) = viewModel.nombres
            titulo = when {
                puntos[0] > puntos[1] -> "¡Ganó $nombre1!"
                puntos[1] > puntos[0] -> "¡Ganó $nombre2!"
                else -> "¡Empate!"
            }
            subtitulo = "$nombre1: ${puntos[0]} pts · $nombre2: ${puntos[1]} pts"
        } else {
            titulo = "¡Partida terminada!"
            subtitulo = "${resumen.correctas} de ${resumen.totalPreguntas} correctas · racha máxima ${resumen.rachaMaxima}"
        }

        startActivity(
            Intent(this, ResultadoActivity::class.java)
                .putExtra(ResultadoActivity.EXTRA_TITULO, titulo)
                .putExtra(ResultadoActivity.EXTRA_SUBTITULO, subtitulo)
                .putExtra(ResultadoActivity.EXTRA_PUNTOS, puntos[0])
                .putExtra(ResultadoActivity.EXTRA_NIVEL, resumen.nivel)
                .putExtra(ResultadoActivity.EXTRA_SUBIO_NIVEL, resumen.subioDeNivel)
                .putExtra(ResultadoActivity.EXTRA_DESENLACE, cierre.desenlace.name)
                .putExtra(ResultadoActivity.EXTRA_PUNTOS_TOTALES, resumen.puntosTotales)
                .putExtra(ResultadoActivity.EXTRA_MODO, viewModel.modo)
                // Para la revancha del duelo local: mismos jugadores y misma cantidad de preguntas.
                .putExtra(EXTRA_NOMBRES, viewModel.nombres.toTypedArray())
                .putExtra(EXTRA_RONDAS, viewModel.rondasPorJugador)
                .putStringArrayListExtra(ResultadoActivity.EXTRA_LOGROS, ArrayList(resumen.logrosNuevos))
        )
        finish()
    }
}
