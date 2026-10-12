package com.calypso.quizzly

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.calypso.quizzly.game.Desenlace
import com.calypso.quizzly.game.LogicaJuego
import com.calypso.quizzly.util.Sonidos
import com.google.android.material.button.MaterialButton

/** Resumen de la partida ya guardada: puntaje, progreso y logros desbloqueados (RF-09). */
class ResultadoActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TITULO = "titulo"
        const val EXTRA_SUBTITULO = "subtitulo"
        const val EXTRA_PUNTOS = "puntos"
        const val EXTRA_NIVEL = "nivel"
        const val EXTRA_SUBIO_NIVEL = "subioNivel"
        const val EXTRA_PUNTOS_TOTALES = "puntosTotales"
        const val EXTRA_LOGROS = "logros"
        const val EXTRA_MODO = "modo"
        const val EXTRA_DESENLACE = "desenlace"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_resultado)

        findViewById<TextView>(R.id.tvTitulo).text =
            intent.getStringExtra(EXTRA_TITULO) ?: "¡Partida terminada!"

        findViewById<TextView>(R.id.tvPuntos).text =
            intent.getIntExtra(EXTRA_PUNTOS, 0).toString()

        findViewById<TextView>(R.id.tvDetalle).text =
            intent.getStringExtra(EXTRA_SUBTITULO).orEmpty()

        val nivel = intent.getIntExtra(EXTRA_NIVEL, 1)
        val subioDeNivel = intent.getBooleanExtra(EXTRA_SUBIO_NIVEL, false)
        val textoNivel = if (subioDeNivel) "⬆️ ¡Subiste a nivel $nivel!" else "Nivel $nivel"

        findViewById<TextView>(R.id.tvNivel).text =
            "$textoNivel · ${intent.getIntExtra(EXTRA_PUNTOS_TOTALES, 0)} puntos totales"

        val logros = intent.getStringArrayListExtra(EXTRA_LOGROS).orEmpty()
        val tvLogros = findViewById<TextView>(R.id.tvLogrosNuevos)
        if (logros.isNotEmpty()) {
            tvLogros.visibility = View.VISIBLE
            tvLogros.text = "🏆 Logro desbloqueado: " + logros.joinToString(" · ") {
                LogicaJuego.nombresDeLogros[it] ?: it
            }
        }

        // Solo la primera vez: si se rota la pantalla no se repite la secuencia.
        if (savedInstanceState == null) {
            val desenlace = intent.getStringExtra(EXTRA_DESENLACE)
                ?.let { Desenlace.valueOf(it) } ?: Desenlace.GANADA
            Sonidos.finDePartida(this, desenlace, subioDeNivel, hayLogros = logros.isNotEmpty())
        }

        val modo = intent.getStringExtra(EXTRA_MODO) ?: GameActivity.MODO_INDIVIDUAL

        findViewById<MaterialButton>(R.id.btnJugarDeNuevo).setOnClickListener {
            Sonidos.toque(this)
            val otraPartida = Intent(this, GameActivity::class.java).putExtra(GameActivity.EXTRA_MODO, modo)

            // En el duelo local, la revancha es con los mismos jugadores y la misma cantidad de preguntas.
            if (modo == GameActivity.MODO_DUELO) {
                otraPartida
                    .putExtra(GameActivity.EXTRA_NOMBRES, intent.getStringArrayExtra(GameActivity.EXTRA_NOMBRES))
                    .putExtra(
                        GameActivity.EXTRA_RONDAS,
                        intent.getIntExtra(GameActivity.EXTRA_RONDAS, LogicaJuego.RONDAS_DE_DUELO_POR_DEFECTO)
                    )
            }
            startActivity(otraPartida)
            finish()
        }

        findViewById<MaterialButton>(R.id.btnVolverMenu).setOnClickListener {
            Sonidos.toque(this)
            startActivity(
                Intent(this, HomeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            finish()
        }
    }

    override fun onPause() {
        // El logro dura 8 s: que no siga sonando en la pantalla siguiente.
        Sonidos.detenerPista()
        super.onPause()
    }
}
