package com.calypso.quizzly

import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.calypso.quizzly.game.LogicaJuego
import com.calypso.quizzly.util.Sonidos
import com.calypso.quizzly.viewmodel.SalaDueloViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText

/**
 * Sala del duelo local (P7): los nombres de los dos jugadores y cuántas preguntas responde cada uno.
 * El duelo se juega en este celular, pasándolo de mano en cada turno.
 */
class SalaDueloActivity : AppCompatActivity() {

    private val viewModel: SalaDueloViewModel by viewModels()

    // Los tres botones del selector, en el mismo orden que las opciones de LogicaJuego (5, 10 y 15).
    private val rondasPorBoton =
        listOf(R.id.btnRondas5, R.id.btnRondas10, R.id.btnRondas15).zip(LogicaJuego.RONDAS_DE_DUELO).toMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sala_duelo)

        val etJugador1 = findViewById<TextInputEditText>(R.id.etJugador1)
        val etJugador2 = findViewById<TextInputEditText>(R.id.etJugador2)
        val grupoRondas = findViewById<MaterialButtonToggleGroup>(R.id.grupoRondas)
        val btnComenzar = findViewById<MaterialButton>(R.id.btnComenzarDuelo)

        listOf(etJugador1, etJugador2).forEach {
            it.filters = arrayOf(InputFilter.LengthFilter(LogicaJuego.LARGO_MAXIMO_DE_NOMBRE))
        }

        // Comenzar se habilita recién cuando están los dos nombres.
        fun actualizarBoton() {
            btnComenzar.isEnabled = !etJugador1.text.isNullOrBlank() && !etJugador2.text.isNullOrBlank()
        }
        etJugador1.doAfterTextChanged { actualizarBoton() }
        etJugador2.doAfterTextChanged { actualizarBoton() }
        actualizarBoton()

        // Solo la primera vez: si se gira el celular, el campo ya conserva lo que se escribió.
        viewModel.nombreDelUsuario.observe(this) { nombre ->
            if (savedInstanceState == null && etJugador1.text.isNullOrEmpty()) etJugador1.setText(nombre)
        }

        grupoRondas.addOnButtonCheckedListener { _, _, marcado ->
            if (marcado) Sonidos.toque(this)
        }

        btnComenzar.setOnClickListener {
            val nombre1 = etJugador1.text.toString().trim()
            val nombre2 = etJugador2.text.toString().trim()

            LogicaJuego.errorEnNombresDeDuelo(nombre1, nombre2)?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Sonidos.toque(this)
            startActivity(
                Intent(this, GameActivity::class.java)
                    .putExtra(GameActivity.EXTRA_MODO, GameActivity.MODO_DUELO)
                    .putExtra(GameActivity.EXTRA_NOMBRES, arrayOf(nombre1, nombre2))
                    .putExtra(
                        GameActivity.EXTRA_RONDAS,
                        rondasPorBoton[grupoRondas.checkedButtonId] ?: LogicaJuego.RONDAS_DE_DUELO_POR_DEFECTO
                    )
            )
            // Al terminar el duelo (o salir de él) se vuelve al menú, no a la sala.
            finish()
        }

        findViewById<TextView>(R.id.tvVolver).setOnClickListener {
            Sonidos.toque(this)
            finish()
        }
    }
}
