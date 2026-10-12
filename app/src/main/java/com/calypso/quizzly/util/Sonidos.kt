package com.calypso.quizzly.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import com.calypso.quizzly.R
import com.calypso.quizzly.game.Desenlace

/**
 * Efectos de sonido del juego, en los mismos puntos donde vibra el celular (ver [Vibracion]).
 *
 * Hay dos canales:
 * - Efectos cortos (toques, aciertos, ruleta): van por SoundPool, que los tiene precargados
 *   y suenan sin demora. Pueden superponerse entre sí.
 * - Pistas largas (cronómetro, racha, cierre de partida): van por MediaPlayer, porque SoundPool
 *   no admite sonidos tan largos. Suena una sola a la vez: la nueva corta a la anterior.
 */
object Sonidos {

    private const val PREFS = "quizzly_ajustes"
    private const val CLAVE_SILENCIADO = "silenciado"

    private val EFECTOS = listOf(
        R.raw.apretarsonido,
        R.raw.preguntacorrecta,
        R.raw.error,
        R.raw.tiempoagotado,
        R.raw.girarruleta,
        R.raw.ruletadetenida,
        R.raw.duelo_cambiodeturno
    )

    private var pool: SoundPool? = null
    private val idsCargados = mutableMapOf<Int, Int>()
    private var pista: MediaPlayer? = null

    /** Precarga los efectos cortos. Conviene llamarlo al arrancar la app; repetirlo no hace nada. */
    fun inicializar(context: Context) {
        if (pool != null) return

        val nuevo = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()

        EFECTOS.forEach { idsCargados[it] = nuevo.load(context.applicationContext, it, 1) }
        pool = nuevo
    }

    // --- Silencio (se recuerda entre sesiones) ---

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun estaSilenciado(context: Context): Boolean =
        prefs(context).getBoolean(CLAVE_SILENCIADO, false)

    /** Activa o desactiva el sonido y devuelve si quedó silenciado. */
    fun alternarSilencio(context: Context): Boolean {
        val silenciado = !estaSilenciado(context)
        prefs(context).edit().putBoolean(CLAVE_SILENCIADO, silenciado).apply()
        if (silenciado) detenerPista()
        return silenciado
    }

    // --- Efectos cortos ---

    private fun efecto(context: Context, recurso: Int) {
        if (estaSilenciado(context)) return
        inicializar(context)
        val id = idsCargados[recurso] ?: return
        pool?.play(id, 1f, 1f, 1, 0, 1f)
    }

    /** Toque breve al pulsar un botón. */
    fun toque(context: Context) = efecto(context, R.raw.apretarsonido)

    fun acierto(context: Context) = efecto(context, R.raw.preguntacorrecta)

    fun error(context: Context) = efecto(context, R.raw.error)

    fun tiempoAgotado(context: Context) = efecto(context, R.raw.tiempoagotado)

    fun girarRuleta(context: Context) = efecto(context, R.raw.girarruleta)

    fun ruletaDetenida(context: Context) = efecto(context, R.raw.ruletadetenida)

    fun cambioDeTurno(context: Context) = efecto(context, R.raw.duelo_cambiodeturno)

    // --- Pistas largas ---

    /** Reproduce las pistas en orden, una detrás de la otra, cortando lo que estuviera sonando. */
    private fun pistas(context: Context, recursos: List<Int>) {
        detenerPista()
        if (estaSilenciado(context) || recursos.isEmpty()) return

        val app = context.applicationContext
        val reproductor = MediaPlayer.create(app, recursos.first()) ?: return

        reproductor.setOnCompletionListener {
            it.release()
            if (pista === it) pista = null
            pistas(app, recursos.drop(1))
        }
        pista = reproductor
        reproductor.start()
    }

    /** Cuenta regresiva de los últimos 10 segundos de la pregunta. */
    fun cronometro(context: Context) = pistas(context, listOf(R.raw.cronometropregunta))

    /** Racha de 3 o de 5: reemplaza al sonido de acierto. */
    fun racha(context: Context) = pistas(context, listOf(R.raw.racha))

    /** Cierre de partida: primero el desenlace, después la subida de nivel y el logro si los hubo. */
    fun finDePartida(context: Context, desenlace: Desenlace, subioDeNivel: Boolean, hayLogros: Boolean) {
        val sonidoDesenlace = when (desenlace) {
            Desenlace.GANADA -> R.raw.partidaganada
            Desenlace.PERDIDA -> R.raw.partidaperdida
            Desenlace.EMPATE -> R.raw.duelo_empate
        }
        pistas(
            context,
            listOfNotNull(
                sonidoDesenlace,
                R.raw.subidanivel.takeIf { subioDeNivel },
                R.raw.logrodesbloqueado.takeIf { hayLogros }
            )
        )
    }

    fun detenerPista() {
        pista?.release()
        pista = null
    }
}
