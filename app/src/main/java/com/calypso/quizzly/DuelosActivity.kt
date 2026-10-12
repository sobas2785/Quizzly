package com.calypso.quizzly

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.calypso.quizzly.game.Desenlace
import com.calypso.quizzly.game.DueloOnline
import com.calypso.quizzly.game.EstadoDuelo
import com.calypso.quizzly.game.LogicaJuego
import com.calypso.quizzly.game.ReglasDueloOnline
import com.calypso.quizzly.util.Sonidos
import com.calypso.quizzly.util.alRecibir
import com.calypso.quizzly.viewmodel.DuelosViewModel
import com.calypso.quizzly.viewmodel.EfectoDuelos
import com.calypso.quizzly.viewmodel.EstadoDuelos
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator

/**
 * "Mis duelos": los duelos online por turnos del usuario, en vivo. Desde acá se crea un duelo,
 * se entra a uno con el código de invitación y se juega el turno cuando toca.
 * Los datos y las acciones están en [DuelosViewModel].
 */
class DuelosActivity : AppCompatActivity() {

    private val viewModel: DuelosViewModel by viewModels()

    private lateinit var cargando: CircularProgressIndicator
    private lateinit var tvEstado: TextView
    private lateinit var btnReintentar: MaterialButton
    private lateinit var btnCrear: MaterialButton
    private lateinit var btnUnirse: MaterialButton
    private lateinit var contenedor: LinearLayout

    // El turno se juega en la pantalla de la ruleta; al volver, la lista ya se actualizó sola.
    private val turno = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { resultado ->
        if (resultado.resultCode == RESULT_OK) {
            val puntos = resultado.data?.getIntExtra(GameActivity.RESULTADO_PUNTOS_DEL_TURNO, 0) ?: 0
            avisar(if (puntos > 0) "Turno enviado: +$puntos puntos" else "Turno enviado: esta vez no sumaste")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_duelos)

        cargando = findViewById(R.id.cargandoDuelos)
        tvEstado = findViewById(R.id.tvEstadoDuelos)
        btnReintentar = findViewById(R.id.btnReintentarDuelos)
        btnCrear = findViewById(R.id.btnCrearDuelo)
        btnUnirse = findViewById(R.id.btnUnirse)
        contenedor = findViewById(R.id.contenedorDuelos)

        findViewById<TextView>(R.id.tvVolver).setOnClickListener {
            Sonidos.toque(this)
            finish()
        }
        btnCrear.setOnClickListener {
            Sonidos.toque(this)
            viewModel.crear()
        }
        btnUnirse.setOnClickListener {
            Sonidos.toque(this)
            pedirCodigo()
        }
        btnReintentar.setOnClickListener {
            Sonidos.toque(this)
            viewModel.conectar()
        }

        viewModel.estado.observe(this, ::mostrar)
        viewModel.ocupado.observe(this) { habilitarBotones() }
        alRecibir(viewModel.efectos, ::reaccionar)
    }

    /** Crear y unirse solo se pueden usar con la lista cargada y sin otra acción en curso. */
    private fun habilitarBotones() {
        val sePuede = viewModel.estado.value is EstadoDuelos.Lista && viewModel.ocupado.value != true
        btnCrear.isEnabled = sePuede
        btnUnirse.isEnabled = sePuede
    }

    override fun onPause() {
        // El sonido del resultado de un duelo no tiene que seguir en la pantalla siguiente.
        Sonidos.detenerPista()
        super.onPause()
    }

    private fun mostrar(estado: EstadoDuelos) {
        contenedor.removeAllViews()
        cargando.visibility = if (estado is EstadoDuelos.Cargando) View.VISIBLE else View.GONE

        habilitarBotones()
        btnReintentar.visibility =
            if (estado is EstadoDuelos.NoDisponible && estado.sePuedeReintentar) View.VISIBLE else View.GONE

        val mensaje = when (estado) {
            is EstadoDuelos.NoDisponible -> estado.motivo
            is EstadoDuelos.Lista ->
                if (estado.duelos.isEmpty()) "Todavía no tenés duelos. Creá uno y pasale el código a tu rival." else null
            EstadoDuelos.Cargando -> null
        }
        tvEstado.text = mensaje
        tvEstado.visibility = if (mensaje != null) View.VISIBLE else View.GONE

        if (estado is EstadoDuelos.Lista) {
            estado.duelos.forEach { contenedor.addView(tarjetaDe(it, estado.miJugadorId)) }
        }
    }

    private fun tarjetaDe(duelo: DueloOnline, miJugadorId: String): View {
        val tarjeta = LayoutInflater.from(this).inflate(R.layout.item_duelo, contenedor, false) as MaterialCardView
        val numero = duelo.numeroDe(miJugadorId) ?: 1
        val rival = duelo.rivalDe(miJugadorId)
        val esMiTurno = duelo.esTurnoDe(miJugadorId)

        tarjeta.findViewById<TextView>(R.id.tvInicialRival).text = rival?.take(1)?.uppercase() ?: "?"
        tarjeta.findViewById<TextView>(R.id.tvTituloDuelo).text = rival?.let { "Contra $it" } ?: "Esperando rival"

        val marcador = "Vos ${duelo.puntosDe(numero)} · ${duelo.puntosDe(3 - numero)} ${rival ?: ""}".trim()
        tarjeta.findViewById<TextView>(R.id.tvDetalleDuelo).text = when (duelo.estado) {
            EstadoDuelo.ESPERANDO -> "Código ${duelo.codigo} · tocá para compartirlo"
            EstadoDuelo.EN_CURSO -> "$marcador · turno ${duelo.turnosJugados + 1} de ${ReglasDueloOnline.TURNOS_TOTALES}"
            EstadoDuelo.TERMINADO -> marcador
        }

        val (etiqueta, color) = when {
            duelo.estado == EstadoDuelo.ESPERANDO -> "Esperando" to 0xFF9CA3AF.toInt()
            esMiTurno -> "Tu turno" to 0xFF7C3AED.toInt()
            duelo.estado == EstadoDuelo.EN_CURSO -> "Le toca" to 0xFF9CA3AF.toInt()
            else -> when (duelo.desenlacePara(miJugadorId)) {
                Desenlace.GANADA -> "Ganaste" to 0xFF059669.toInt()
                Desenlace.PERDIDA -> "Perdiste" to 0xFFDC2626.toInt()
                else -> "Empate" to 0xFF2563EB.toInt()
            }
        }
        tarjeta.findViewById<TextView>(R.id.tvEstadoDuelo).apply {
            text = etiqueta
            backgroundTintList = ColorStateList.valueOf(color)
        }
        if (esMiTurno) tarjeta.strokeColor = 0xFFA78BFA.toInt()

        tarjeta.findViewById<TextView>(R.id.tvCancelarDuelo).apply {
            visibility = if (duelo.estado == EstadoDuelo.ESPERANDO) View.VISIBLE else View.GONE
            setOnClickListener {
                Sonidos.toque(this@DuelosActivity)
                viewModel.cancelar(duelo)
            }
        }

        tarjeta.setOnClickListener {
            Sonidos.toque(this)
            when {
                duelo.estado == EstadoDuelo.ESPERANDO -> compartir(duelo.codigo)
                esMiTurno -> jugar(duelo, numero)
                duelo.estado == EstadoDuelo.EN_CURSO -> avisar("Le toca a $rival. Cuando juegue, vas a ver \"Tu turno\".")
            }
        }
        return tarjeta
    }

    private fun jugar(duelo: DueloOnline, numero: Int) {
        turno.launch(
            Intent(this, GameActivity::class.java)
                .putExtra(GameActivity.EXTRA_MODO, GameActivity.MODO_DUELO_ONLINE)
                .putExtra(GameActivity.EXTRA_CODIGO_DUELO, duelo.codigo)
                .putExtra(GameActivity.EXTRA_MI_NUMERO, numero)
                .putExtra(GameActivity.EXTRA_NOMBRES, arrayOf(duelo.nombreDe(1), duelo.nombreDe(2)))
                .putExtra(GameActivity.EXTRA_PUNTOS, intArrayOf(duelo.puntos1, duelo.puntos2))
        )
    }

    private fun pedirCodigo() {
        val margen = (20 * resources.displayMetrics.density).toInt()
        val campo = EditText(this).apply {
            hint = "Código de ${ReglasDueloOnline.LARGO_DEL_CODIGO} letras"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            filters = arrayOf(InputFilter.LengthFilter(ReglasDueloOnline.LARGO_DEL_CODIGO + 2), InputFilter.AllCaps())
        }
        val caja = FrameLayout(this).apply {
            setPadding(margen, margen / 2, margen, 0)
            addView(campo)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Unirme a un duelo")
            .setMessage("Escribí el código que te pasó tu rival.")
            .setView(caja)
            .setPositiveButton("Unirme") { _, _ -> viewModel.unirse(campo.text.toString()) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun compartir(codigo: String) {
        val mensaje = "Te desafío a un duelo en Quizzly. Entrá a Duelos online, tocá \"Tengo un código\" y poné: $codigo"
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, mensaje),
                "Compartir el código"
            )
        )
    }

    private fun reaccionar(efecto: EfectoDuelos) {
        when (efecto) {
            is EfectoDuelos.Aviso -> avisar(efecto.mensaje)

            is EfectoDuelos.Creado ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("Duelo creado")
                    .setMessage("Código: ${efecto.duelo.codigo}\n\nPasáselo a tu rival. Cuando se una, el primer turno es tuyo.")
                    .setPositiveButton("Compartir") { _, _ -> compartir(efecto.duelo.codigo) }
                    .setNegativeButton("Listo", null)
                    .show()

            is EfectoDuelos.Cerrado -> mostrarResultado(efecto)
        }
    }

    /** El duelo terminó y ya quedó guardado como partida: se festeja (o no) igual que una partida local. */
    private fun mostrarResultado(cierre: EfectoDuelos.Cerrado) {
        val resumen = cierre.resumen
        val rival = cierre.duelo.rivalDe((viewModel.estado.value as? EstadoDuelos.Lista)?.miJugadorId.orEmpty()) ?: "tu rival"

        val titulo = when (cierre.desenlace) {
            Desenlace.GANADA -> "¡Le ganaste a $rival!"
            Desenlace.PERDIDA -> "Esta vez ganó $rival"
            Desenlace.EMPATE -> "¡Empate con $rival!"
        }
        val lineas = mutableListOf(
            "Sumaste ${resumen.puntos} puntos con ${resumen.correctas} de ${resumen.totalPreguntas} correctas.",
            if (resumen.subioDeNivel) "⬆️ ¡Subiste a nivel ${resumen.nivel}!" else "Nivel ${resumen.nivel} · ${resumen.puntosTotales} puntos totales"
        )
        if (resumen.logrosNuevos.isNotEmpty()) {
            lineas += "🏆 Logro desbloqueado: " + resumen.logrosNuevos.joinToString(" · ") { LogicaJuego.nombresDeLogros[it] ?: it }
        }

        Sonidos.finDePartida(this, cierre.desenlace, resumen.subioDeNivel, hayLogros = resumen.logrosNuevos.isNotEmpty())
        MaterialAlertDialogBuilder(this)
            .setTitle(titulo)
            .setMessage(lineas.joinToString("\n\n"))
            .setPositiveButton("Listo", null)
            .setOnDismissListener { Sonidos.detenerPista() }
            .show()
    }

    private fun avisar(mensaje: String) = Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
}
