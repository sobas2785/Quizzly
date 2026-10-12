package com.calypso.quizzly

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.calypso.quizzly.game.Periodo
import com.calypso.quizzly.util.Sonidos
import com.calypso.quizzly.viewmodel.EstadoRanking
import com.calypso.quizzly.viewmodel.RankingViewModel
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.CircularProgressIndicator

/**
 * Ranking de jugadores (RF-10) con filtros por día, semana, mes y global.
 * La consulta (a Firebase o a la base local) la hace [RankingViewModel].
 */
class RankingActivity : AppCompatActivity() {

    private val viewModel: RankingViewModel by viewModels()

    private lateinit var cargando: CircularProgressIndicator
    private lateinit var tvEstado: TextView
    private lateinit var contenedor: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ranking)

        cargando = findViewById(R.id.cargandoRanking)
        tvEstado = findViewById(R.id.tvEstadoRanking)
        contenedor = findViewById(R.id.contenedorRanking)

        findViewById<TextView>(R.id.tvVolver).setOnClickListener {
            Sonidos.toque(this)
            finish()
        }

        findViewById<TextView>(R.id.tvFuenteRanking).text =
            if (viewModel.online) "Jugadores de Quizzly en todos los celulares"
            else "Jugadores de este celular · el ranking online todavía no está configurado"

        val botones = mapOf(
            Periodo.DIA to R.id.btnHoy,
            Periodo.SEMANA to R.id.btnSemana,
            Periodo.MES to R.id.btnMes,
            Periodo.GLOBAL to R.id.btnGlobal
        )
        val grupo = findViewById<MaterialButtonToggleGroup>(R.id.grupoPeriodo)

        // Antes de escuchar los toques, el filtro marcado es el que ya tiene el ViewModel
        // (si la pantalla se recreó, puede no ser el del layout).
        botones[viewModel.periodo]?.let { grupo.check(it) }

        grupo.addOnButtonCheckedListener { _, idBoton, marcado ->
            val periodo = botones.entries.firstOrNull { it.value == idBoton }?.key
            if (marcado && periodo != null && viewModel.elegir(periodo)) Sonidos.toque(this)
        }

        viewModel.estado.observe(this, ::mostrar)
    }

    private fun mostrar(estado: EstadoRanking) {
        contenedor.removeAllViews()
        cargando.visibility = if (estado is EstadoRanking.Cargando) View.VISIBLE else View.GONE

        val mensaje = when (estado) {
            EstadoRanking.Vacio -> "Todavía no hay partidas en este período. ¡Estrenalo vos!"
            EstadoRanking.Error -> "No se pudo cargar el ranking. Revisá la conexión e intentá de nuevo."
            else -> null
        }
        tvEstado.text = mensaje
        tvEstado.visibility = if (mensaje != null) View.VISIBLE else View.GONE

        if (estado is EstadoRanking.Tabla) mostrarFilas(estado)
    }

    private fun mostrarFilas(tabla: EstadoRanking.Tabla) {
        val inflater = LayoutInflater.from(this)

        tabla.filas.forEachIndexed { i, fila ->
            val tarjeta = inflater.inflate(R.layout.item_ranking, contenedor, false) as MaterialCardView
            val esMio = fila.jugadorId == tabla.miJugadorId

            tarjeta.findViewById<TextView>(R.id.tvPosicion).text = when (i) {
                0 -> "🥇"
                1 -> "🥈"
                2 -> "🥉"
                else -> "${i + 1}"
            }
            tarjeta.findViewById<TextView>(R.id.tvInicialRanking).text = fila.nombre.take(1).uppercase()
            tarjeta.findViewById<TextView>(R.id.tvNombreRanking).text =
                if (esMio) "${fila.nombre} (vos)" else fila.nombre
            tarjeta.findViewById<TextView>(R.id.tvPuntosRanking).text = "${fila.puntos} pts"

            if (esMio) {
                tarjeta.setCardBackgroundColor(0xFFF5F3FF.toInt())
                tarjeta.strokeColor = 0xFFA78BFA.toInt()
            }
            contenedor.addView(tarjeta)
        }
    }
}
