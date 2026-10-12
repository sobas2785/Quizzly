package com.calypso.quizzly.util

import android.content.Context

/** Guarda qué usuario está logueado, para no tener que pasarlo por Intent en cada pantalla. */
object Sesion {

    private const val PREFS = "quizzly_sesion"
    private const val CLAVE_USUARIO = "usuarioId"
    const val SIN_SESION = -1L

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun iniciar(context: Context, usuarioId: Long) {
        prefs(context).edit().putLong(CLAVE_USUARIO, usuarioId).apply()
    }

    fun usuarioId(context: Context): Long =
        prefs(context).getLong(CLAVE_USUARIO, SIN_SESION)

    fun cerrar(context: Context) {
        prefs(context).edit().remove(CLAVE_USUARIO).apply()
    }
}
