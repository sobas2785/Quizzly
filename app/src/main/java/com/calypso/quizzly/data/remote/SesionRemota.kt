package com.calypso.quizzly.data.remote

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * La identidad de este celular en Firebase, compartida por el ranking y los duelos online.
 * Es una sesión anónima de Firebase Auth: no pide datos al usuario y alcanza para que las reglas
 * de Firestore sepan qué documentos son de cada instalación.
 */
object SesionRemota {

    private const val MS_PARA_INICIAR_SESION = 10_000L

    /** false si el proyecto no tiene google-services.json: Firebase no se inicializa y lo online se desactiva. */
    fun disponible(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()

    /** El uid de la sesión ya abierta, o null si este celular todavía no inició sesión. */
    fun uidActual(context: Context): String? =
        if (disponible(context)) FirebaseAuth.getInstance().currentUser?.uid else null

    /** La sesión anónima se crea una sola vez por instalación y Firebase la recuerda. La primera vez necesita red. */
    suspend fun uid(): String {
        val auth = FirebaseAuth.getInstance()
        auth.currentUser?.let { return it.uid }
        return withTimeout(MS_PARA_INICIAR_SESION) {
            auth.signInAnonymously().await().user!!.uid
        }
    }

    /**
     * Cómo se identifica un usuario local en lo online. En un mismo celular puede haber varias
     * cuentas locales, que comparten el uid: por eso se le suma el id del usuario.
     */
    fun jugadorId(uid: String, usuarioId: Long) = "${uid}_$usuarioId"
}
