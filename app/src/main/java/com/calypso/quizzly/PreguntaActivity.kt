package com.calypso.quizzly

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import com.calypso.quizzly.game.Categorias
import com.calypso.quizzly.game.LogicaJuego
import com.calypso.quizzly.util.Sonidos
import com.calypso.quizzly.util.Vibracion
import com.calypso.quizzly.util.alRecibir
import com.calypso.quizzly.viewmodel.EfectoRespuesta
import com.calypso.quizzly.viewmodel.EstadoPregunta
import com.calypso.quizzly.viewmodel.PreguntaViewModel
import com.calypso.quizzly.viewmodel.ResultadoRonda
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator

/**
 * Ronda de preguntas de una categoría (RF-05, RF-06, RF-14).
 * La lógica está en [PreguntaViewModel]; esta pantalla dibuja el estado, suena y vibra.
 * Devuelve el resultado a quien la lanzó: no guarda nada en la base por su cuenta,
 * porque la misma pantalla se usa para la partida individual y para el duelo local.
 */
class PreguntaActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CATEGORIA_ID = "categoriaId"
        const val EXTRA_CANTIDAD = "cantidad"
        const val EXTRA_ETIQUETA_JUGADOR = "etiquetaJugador"

        /** El modo de juego (GameActivity.MODO_*): define qué pasa si el jugador sale de la ronda. */
        const val EXTRA_MODO = "modo"

        const val RESULTADO_PUNTOS = "puntos"
        const val RESULTADO_CORRECTAS = "correctas"
        const val RESULTADO_TOTAL = "total"
        const val RESULTADO_RACHA_MAXIMA = "rachaMaxima"

        /** El sonido del cronómetro dura 10 s: arranca cuando quedan 10 y termina con el tiempo. */
        private const val SEGUNDOS_DE_CRONOMETRO = 10
    }

    private val viewModel: PreguntaViewModel by viewModels()

    private lateinit var tvProgreso: TextView
    private lateinit var tvTiempo: TextView
    private lateinit var tvPregunta: TextView
    private lateinit var tvRacha: TextView
    private lateinit var progressTiempo: LinearProgressIndicator
    private lateinit var botones: List<MaterialButton>

    /** Número de la pregunta para la que ya arrancó el sonido del cronómetro. */
    private var cronometroDePregunta = 0

    private var dialogoSalida: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pregunta)

        tvProgreso = findViewById(R.id.tvProgreso)
        tvTiempo = findViewById(R.id.tvTiempo)
        tvPregunta = findViewById(R.id.tvPregunta)
        tvRacha = findViewById(R.id.tvRacha)
        progressTiempo = findViewById(R.id.progressTiempo)
        progressTiempo.max = LogicaJuego.SEGUNDOS_POR_PREGUNTA

        botones = listOf(
            findViewById(R.id.btnOpcionA),
            findViewById(R.id.btnOpcionB),
            findViewById(R.id.btnOpcionC),
            findViewById(R.id.btnOpcionD)
        )

        val categoriaId = intent.getIntExtra(EXTRA_CATEGORIA_ID, 1)
        val categoria = Categorias.porId(categoriaId)
        findViewById<ImageView>(R.id.ivCategoria).setImageResource(categoria.ilustracion)
        findViewById<TextView>(R.id.tvCategoria).text = categoria.nombre
        findViewById<TextView>(R.id.tvJugador).text =
            intent.getStringExtra(EXTRA_ETIQUETA_JUGADOR) ?: "Partida individual"

        botones.forEachIndexed { i, boton ->
            boton.setOnClickListener { viewModel.responder("ABCD"[i].toString()) }
        }

        viewModel.iniciar(
            categoriaId,
            intent.getIntExtra(EXTRA_CANTIDAD, LogicaJuego.PREGUNTAS_POR_PARTIDA)
        )
        viewModel.estado.observe(this, ::mostrar)
        alRecibir(viewModel.efectos, ::reaccionar)

        // "Atrás" en medio de la ronda pide confirmación, para no salir por un toque sin querer.
        // El temporizador sigue corriendo mientras tanto: frenarlo daría tiempo extra para pensar.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = confirmarSalida()
        })
    }

    override fun onDestroy() {
        dialogoSalida?.dismiss()
        super.onDestroy()
    }

    private fun confirmarSalida() {
        if (dialogoSalida?.isShowing == true) return

        val (titulo, mensaje) = when (intent.getStringExtra(EXTRA_MODO)) {
            GameActivity.MODO_DUELO_ONLINE ->
                "¿Salir de la pregunta?" to "Si salís, cuenta como respuesta incorrecta y el turno pasa a tu rival."
            GameActivity.MODO_DUELO ->
                "¿Salir de la pregunta?" to "Volvés a la ruleta sin perder el turno."
            else ->
                "¿Salir de la partida?" to "Se pierde lo que jugaste en esta partida y no se guarda en tu historial."
        }

        dialogoSalida = MaterialAlertDialogBuilder(this)
            .setTitle(titulo)
            .setMessage(mensaje)
            .setPositiveButton("Salir") { _, _ -> finish() }
            .setNegativeButton("Seguir jugando", null)
            .show()
    }

    override fun onPause() {
        // Que el cronómetro o la racha no sigan sonando si se sale de la pantalla. Va en onPause
        // y no en onStop porque onStop puede llegar tarde y cortar el sonido del resultado.
        Sonidos.detenerPista()
        super.onPause()
    }

    private fun mostrar(estado: EstadoPregunta) {
        estado.resultado?.let {
            devolver(it)
            return
        }
        val pregunta = estado.pregunta ?: return

        tvProgreso.text = "${estado.numero}/${estado.cantidad}"
        tvPregunta.text = pregunta.texto
        tvRacha.text = "Racha: ${estado.racha} · Puntos: ${estado.puntos}"
        progressTiempo.progress = estado.segundosRestantes
        tvTiempo.text = if (estado.tiempoAgotado) "¡Sin tiempo!" else "${estado.segundosRestantes} s"

        val opciones = listOf(pregunta.opcionA, pregunta.opcionB, pregunta.opcionC, pregunta.opcionD)
        botones.forEachIndexed { i, boton ->
            val letra = "ABCD"[i].toString()
            boton.text = "$letra.  ${opciones[i]}"
            boton.isEnabled = !estado.respondida

            // Al responder: verde la correcta y rojo la elegida si estaba mal.
            val fondo = when {
                !estado.respondida -> Color.WHITE
                letra == pregunta.respuesta -> Color.parseColor("#059669")
                letra == estado.letraElegida -> Color.parseColor("#DC2626")
                else -> Color.WHITE
            }
            boton.setBackgroundColor(fondo)
            boton.setTextColor(if (fondo == Color.WHITE) Color.parseColor("#111827") else Color.WHITE)
        }

        // Una sola vez por pregunta, y solo con la pantalla al frente.
        if (!estado.respondida &&
            estado.segundosRestantes <= SEGUNDOS_DE_CRONOMETRO &&
            cronometroDePregunta != estado.numero &&
            lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        ) {
            cronometroDePregunta = estado.numero
            Sonidos.cronometro(this)
        }
    }

    private fun reaccionar(efecto: EfectoRespuesta) {
        // Corta el cronómetro (o la racha anterior) antes de sonar la respuesta.
        Sonidos.detenerPista()

        when (efecto) {
            EfectoRespuesta.TIEMPO_AGOTADO -> {
                Vibracion.tiempoAgotado(this)
                Sonidos.tiempoAgotado(this)
            }

            EfectoRespuesta.ACIERTO -> {
                Vibracion.acierto(this)
                Sonidos.acierto(this)
            }

            EfectoRespuesta.RACHA -> {
                Vibracion.acierto(this)
                Sonidos.racha(this)
            }

            EfectoRespuesta.ERROR -> {
                Vibracion.error(this)
                Sonidos.error(this)
            }
        }
    }

    private fun devolver(resultado: ResultadoRonda) {
        if (isFinishing) return
        setResult(
            RESULT_OK,
            Intent()
                .putExtra(RESULTADO_PUNTOS, resultado.puntos)
                .putExtra(RESULTADO_CORRECTAS, resultado.correctas)
                .putExtra(RESULTADO_TOTAL, resultado.total)
                .putExtra(RESULTADO_RACHA_MAXIMA, resultado.rachaMaxima)
        )
        finish()
    }
}
