package com.calypso.quizzly

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.calypso.quizzly.util.Sesion
import com.calypso.quizzly.util.Sonidos
import com.calypso.quizzly.viewmodel.EstadoHome
import com.calypso.quizzly.viewmodel.HomeViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Menú principal: saludo, progreso del usuario y accesos a las partidas (RF-12 parcial). */
class HomeActivity : AppCompatActivity() {

    private val viewModel: HomeViewModel by viewModels()

    private lateinit var tvGreeting: TextView
    private lateinit var tvStats: TextView
    private lateinit var tvAvatar: TextView
    private lateinit var tvLogros: TextView
    private lateinit var tvSonido: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        tvGreeting = findViewById(R.id.tvGreeting)
        tvStats = findViewById(R.id.tvStats)
        tvAvatar = findViewById(R.id.tvAvatar)
        tvLogros = findViewById(R.id.tvLogros)
        tvSonido = findViewById(R.id.tvSonido)

        mostrarEstadoSonido(Sonidos.estaSilenciado(this))
        tvSonido.setOnClickListener {
            val silenciado = Sonidos.alternarSilencio(this)
            mostrarEstadoSonido(silenciado)
            // Al reactivarlo suena el toque, como confirmación.
            Sonidos.toque(this)
        }

        findViewById<MaterialButton>(R.id.btnPlayFeatured).setOnClickListener {
            Sonidos.toque(this)
            abrirRuleta(GameActivity.MODO_INDIVIDUAL)
        }

        findViewById<MaterialButton>(R.id.btnDuelo).setOnClickListener {
            Sonidos.toque(this)
            abrirRuleta(GameActivity.MODO_DUELO)
        }

        // Cada card arranca una partida individual de esa categoría, sin pasar por la ruleta.
        val cardsPorCategoria = mapOf(
            R.id.cardGeography to 1,
            R.id.cardScience to 2,
            R.id.cardSports to 3,
            R.id.cardEntertainment to 4,
            R.id.cardHistory to 5,
            R.id.cardMusic to 6
        )

        cardsPorCategoria.forEach { (idCard, categoriaId) ->
            findViewById<MaterialCardView>(idCard).setOnClickListener {
                Sonidos.toque(this)
                startActivity(
                    Intent(this, GameActivity::class.java)
                        .putExtra(GameActivity.EXTRA_MODO, GameActivity.MODO_INDIVIDUAL)
                        .putExtra(GameActivity.EXTRA_CATEGORIA_FIJA, categoriaId)
                )
            }
        }

        // Al perfil se entra con el botón o tocando el avatar.
        val abrirPerfil = View.OnClickListener {
            Sonidos.toque(this)
            startActivity(Intent(this, PerfilActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnPerfil).setOnClickListener(abrirPerfil)
        tvAvatar.setOnClickListener(abrirPerfil)

        findViewById<MaterialButton>(R.id.btnRanking).setOnClickListener {
            Sonidos.toque(this)
            startActivity(Intent(this, RankingActivity::class.java))
        }

        val btnDuelosOnline = findViewById<MaterialButton>(R.id.btnDuelosOnline)
        btnDuelosOnline.setOnClickListener {
            Sonidos.toque(this)
            startActivity(Intent(this, DuelosActivity::class.java))
        }
        viewModel.turnosPendientes.observe(this) { pendientes ->
            btnDuelosOnline.text = when (pendientes) {
                0 -> "⚔️  Duelos online"
                1 -> "⚔️  Duelos online · ¡te toca en 1!"
                else -> "⚔️  Duelos online · ¡te toca en $pendientes!"
            }
        }

        findViewById<TextView>(R.id.tvLogout).setOnClickListener {
            Sonidos.toque(this)
            MaterialAlertDialogBuilder(this)
                .setTitle("¿Cerrar sesión?")
                .setMessage("Para volver a jugar vas a tener que ingresar con tu correo y contraseña.")
                .setPositiveButton("Cerrar sesión") { _, _ -> cerrarSesion() }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        // Desde el menú, "Atrás" cierra la app: se pregunta antes, por si fue sin querer.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                MaterialAlertDialogBuilder(this@HomeActivity)
                    .setTitle("¿Salir de Quizzly?")
                    .setPositiveButton("Salir") { _, _ -> finish() }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        })

        viewModel.estado.observe(this, ::mostrar)
    }

    private fun cerrarSesion() {
        Sesion.cerrar(this)
        startActivity(Intent(this, MainActivity::class.java))
        finishAffinity()
    }

    override fun onResume() {
        super.onResume()

        val usuarioId = Sesion.usuarioId(this)
        if (usuarioId == Sesion.SIN_SESION) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        // Los datos se releen al volver de una partida, para ver el progreso actualizado.
        viewModel.cargar(usuarioId)
    }

    private fun mostrar(estado: EstadoHome) {
        val usuario = estado.usuario

        tvGreeting.text = "¡Hola, ${usuario.nombreUsuario}!"
        tvStats.text = "Nivel ${usuario.nivel} · ${usuario.puntosTotales} puntos · mejor racha ${usuario.rachaMaxima}"
        tvAvatar.text = usuario.nombreUsuario.take(1).uppercase()
        tvLogros.text =
            if (estado.logros.isEmpty()) "Logros: todavía ninguno"
            else "Logros: " + estado.logros.joinToString(" · ")
    }

    private fun mostrarEstadoSonido(silenciado: Boolean) {
        tvSonido.text = if (silenciado) "🔇" else "🔊"
        tvSonido.contentDescription = if (silenciado) "Activar sonidos" else "Silenciar sonidos"
    }

    private fun abrirRuleta(modo: String) {
        startActivity(
            Intent(this, GameActivity::class.java).putExtra(GameActivity.EXTRA_MODO, modo)
        )
    }
}
