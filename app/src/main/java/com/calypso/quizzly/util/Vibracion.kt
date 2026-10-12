package com.calypso.quizzly.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Feedback háptico del juego. Los efectos de sonido van en estos mismos puntos (ver [Sonidos]).
 */
object Vibracion {

    private fun vibrador(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private fun reproducir(context: Context, tiempos: LongArray, amplitudes: IntArray) {
        val vibrador = vibrador(context) ?: return
        if (!vibrador.hasVibrator()) return
        vibrador.vibrate(VibrationEffect.createWaveform(tiempos, amplitudes, -1))
    }

    /** Acierto: dos toques cortos y suaves. */
    fun acierto(context: Context) =
        reproducir(context, longArrayOf(0, 35, 60, 35), intArrayOf(0, 120, 0, 160))

    /** Error: un golpe seco y más largo. */
    fun error(context: Context) =
        reproducir(context, longArrayOf(0, 180), intArrayOf(0, 200))

    /** Se acabó el tiempo de la pregunta: tres pulsos descendentes. */
    fun tiempoAgotado(context: Context) =
        reproducir(context, longArrayOf(0, 70, 50, 70, 50, 120), intArrayOf(0, 180, 0, 140, 0, 100))

    /** La ruleta se detuvo en una categoría. */
    fun categoriaElegida(context: Context) =
        reproducir(context, longArrayOf(0, 25, 40, 25, 40, 90), intArrayOf(0, 90, 0, 130, 0, 190))

    /** Toque breve al pulsar un botón. */
    fun toque(context: Context) =
        reproducir(context, longArrayOf(0, 18), intArrayOf(0, 90))
}
