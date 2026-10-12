package com.calypso.quizzly.util

import android.content.Context
import java.util.concurrent.TimeUnit

/**
 * Guarda qué usuario está logueado, para no tener que pasarlo por Intent en cada pantalla.
 * RNF-03: la sesión vence a los 30 días de iniciada; al abrir la app, [usuarioVigente] lo controla.
 */
object Sesion {

    private const val PREFS = "quizzly_sesion"
    private const val CLAVE_USUARIO = "usuarioId"
    private const val CLAVE_INICIO = "inicio"
    const val SIN_SESION = -1L
    const val DIAS_DE_VIGENCIA = 30L

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun iniciar(context: Context, usuarioId: Long) {
        prefs(context).edit()
            .putLong(CLAVE_USUARIO, usuarioId)
            .putLong(CLAVE_INICIO, System.currentTimeMillis())
            .apply()
    }

    fun usuarioId(context: Context): Long =
        prefs(context).getLong(CLAVE_USUARIO, SIN_SESION)

    /** El usuario logueado si la sesión sigue vigente. Si venció, la cierra y devuelve [SIN_SESION]. */
    fun usuarioVigente(context: Context, ahora: Long = System.currentTimeMillis()): Long {
        val usuarioId = usuarioId(context)
        if (usuarioId == SIN_SESION) return SIN_SESION

        val inicio = prefs(context).getLong(CLAVE_INICIO, 0L)
        if (inicio == 0L) {
            // Sesión abierta antes de que existiera el vencimiento: empieza a contar desde ahora.
            prefs(context).edit().putLong(CLAVE_INICIO, ahora).apply()
            return usuarioId
        }
        if (vencio(inicio, ahora)) {
            cerrar(context)
            return SIN_SESION
        }
        return usuarioId
    }

    fun vencio(inicio: Long, ahora: Long): Boolean =
        ahora - inicio > TimeUnit.DAYS.toMillis(DIAS_DE_VIGENCIA)

    fun cerrar(context: Context) {
        prefs(context).edit().remove(CLAVE_USUARIO).remove(CLAVE_INICIO).apply()
    }
}
