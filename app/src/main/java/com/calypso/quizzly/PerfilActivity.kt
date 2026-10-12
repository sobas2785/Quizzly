package com.calypso.quizzly

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.calypso.quizzly.data.local.entity.LogroUsuario
import com.calypso.quizzly.data.local.entity.Partida
import com.calypso.quizzly.data.local.entity.Usuario
import com.calypso.quizzly.game.Categorias
import com.calypso.quizzly.game.LogicaJuego
import com.calypso.quizzly.util.Sonidos
import com.calypso.quizzly.viewmodel.PerfilViewModel
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Perfil del jugador (RF-12): datos, progreso de nivel, galería de logros e historial de partidas.
 * Los datos los carga [PerfilViewModel].
 */
class PerfilActivity : AppCompatActivity() {

    private val viewModel: PerfilViewModel by viewModels()

    private val formatoDia = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val formatoDiaHora = DateTimeFormatter.ofPattern("dd/MM · HH:mm")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_perfil)

        findViewById<TextView>(R.id.tvVolver).setOnClickListener {
            Sonidos.toque(this)
            finish()
        }

        viewModel.estado.observe(this) { estado ->
            if (estado == null) {
                // No hay usuario logueado para mostrar.
                finish()
                return@observe
            }
            mostrarDatos(estado.usuario, estado.partidasJugadas)
            mostrarLogros(estado.logros)
            mostrarHistorial(estado.historial)
        }
    }

    private fun mostrarDatos(usuario: Usuario, partidasJugadas: Int) {
        findViewById<TextView>(R.id.tvAvatarPerfil).text = usuario.nombreUsuario.take(1).uppercase()
        findViewById<TextView>(R.id.tvNombrePerfil).text = usuario.nombreUsuario
        findViewById<TextView>(R.id.tvMiembroDesde).text =
            "Jugando desde el ${formatear(usuario.fechaRegistro, formatoDia)}"

        findViewById<TextView>(R.id.tvStatNivel).text = usuario.nivel.toString()
        findViewById<TextView>(R.id.tvStatPuntos).text = usuario.puntosTotales.toString()
        findViewById<TextView>(R.id.tvStatRacha).text = usuario.rachaMaxima.toString()
        findViewById<TextView>(R.id.tvStatPartidas).text = partidasJugadas.toString()

        val dentroDelNivel = LogicaJuego.puntosDentroDelNivel(usuario.puntosTotales)
        findViewById<LinearProgressIndicator>(R.id.progressNivel).apply {
            max = LogicaJuego.PUNTOS_POR_NIVEL
            progress = dentroDelNivel
        }
        findViewById<TextView>(R.id.tvProgresoNivel).text =
            "$dentroDelNivel de ${LogicaJuego.PUNTOS_POR_NIVEL} puntos para el nivel ${usuario.nivel + 1}"
    }

    /** Galería de dos columnas con todos los logros: los obtenidos a color y con fecha, el resto bloqueados. */
    private fun mostrarLogros(obtenidos: List<LogroUsuario>) {
        val fechaPorCodigo = obtenidos.associate { it.logroId to it.fechaObtenido }
        findViewById<TextView>(R.id.tvContadorLogros).text =
            "${fechaPorCodigo.size} de ${LogicaJuego.logros.size}"

        val contenedor = findViewById<LinearLayout>(R.id.contenedorLogros)
        contenedor.removeAllViews()
        val inflater = LayoutInflater.from(this)

        LogicaJuego.logros.chunked(2).forEach { par ->
            val fila = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                isBaselineAligned = false
            }
            par.forEach { logro ->
                val tarjeta = inflater.inflate(R.layout.item_logro, fila, false) as MaterialCardView
                val fecha = fechaPorCodigo[logro.codigo]

                tarjeta.findViewById<TextView>(R.id.tvIconoLogro).text = logro.icono
                tarjeta.findViewById<TextView>(R.id.tvNombreLogro).text = logro.nombre
                tarjeta.findViewById<TextView>(R.id.tvDescripcionLogro).text = logro.descripcion

                val estado = tarjeta.findViewById<TextView>(R.id.tvEstadoLogro)
                if (fecha != null) {
                    estado.text = "✓ ${formatear(fecha, formatoDia)}"
                    estado.setTextColor(0xFF059669.toInt())
                    tarjeta.strokeColor = 0xFFA78BFA.toInt()
                } else {
                    estado.text = "🔒 Bloqueado"
                    tarjeta.setCardBackgroundColor(0xFFF9FAFB.toInt())
                    tarjeta.alpha = 0.6f
                }
                fila.addView(tarjeta)
            }
            contenedor.addView(fila)
        }
    }

    private fun mostrarHistorial(historial: List<Partida>) {
        findViewById<TextView>(R.id.tvHistorialVacio).visibility =
            if (historial.isEmpty()) View.VISIBLE else View.GONE

        val contenedor = findViewById<LinearLayout>(R.id.contenedorHistorial)
        contenedor.removeAllViews()
        val inflater = LayoutInflater.from(this)

        historial.forEach { partida ->
            val fila = inflater.inflate(R.layout.item_partida, contenedor, false)
            val categoria = Categorias.todas.firstOrNull { it.id == partida.categoriaId }
            val modo = when (partida.modo) {
                GameActivity.MODO_DUELO -> "Duelo 1v1"
                GameActivity.MODO_DUELO_ONLINE -> "Duelo online"
                else -> "Individual"
            }

            categoria?.let { fila.findViewById<ImageView>(R.id.ivPartidaCategoria).setImageResource(it.ilustracion) }
            fila.findViewById<TextView>(R.id.tvPartidaTitulo).text = "${categoria?.nombre ?: "Partida"} · $modo"
            fila.findViewById<TextView>(R.id.tvPartidaDetalle).text =
                "${partida.correctas} de ${partida.totalPreguntas} correctas · racha ${partida.rachaMaxima}"
            fila.findViewById<TextView>(R.id.tvPartidaPuntos).text = "+${partida.puntos}"
            fila.findViewById<TextView>(R.id.tvPartidaFecha).text = formatear(partida.fecha, formatoDiaHora)

            contenedor.addView(fila)
        }
    }

    private fun formatear(millis: Long, formato: DateTimeFormatter): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(formato)
}
