package com.calypso.quizzly.data.remote

import android.content.Context
import android.util.Log
import com.calypso.quizzly.game.FilaRanking
import com.calypso.quizzly.game.Periodo
import com.calypso.quizzly.game.PuntajeDePartida
import com.calypso.quizzly.game.Ranking
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.ZonedDateTime
import java.util.Date

/** Lo que se publica de un jugador: se toma del usuario local ya actualizado. */
data class JugadorPublicado(val usuarioId: Long, val nombre: String, val puntosTotales: Int, val nivel: Int)

/**
 * Ranking online en Firebase Firestore (RF-10).
 *
 * - "jugadores/{uid}_{usuarioId}": el total de cada jugador, para el ranking global.
 * - "partidas/{auto}": los puntos de cada partida con la hora del servidor, para los filtros
 *   por día, semana y mes (se suman del lado del celular con [Ranking.sumarPorJugador]).
 *
 * Para escribir hace falta la sesión anónima de [SesionRemota]: las reglas de Firestore
 * (firestore.rules en la raíz del proyecto) solo dejan escribir los documentos propios.
 * Si el proyecto no tiene google-services.json, Firebase no se inicializa y [disponible] da false.
 */
object RankingRemoto {

    private const val TAG = "RankingRemoto"
    private const val JUGADORES = "jugadores"
    private const val PARTIDAS = "partidas"

    /** Tope de partidas a leer por período, para no bajar la colección entera. */
    private const val MAX_PARTIDAS_POR_CONSULTA = 2000L

    // Publicar no tiene que demorar la pantalla de resultado ni morir con la Activity.
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun disponible(context: Context): Boolean = SesionRemota.disponible(context)

    /** El id con el que este usuario local aparece en el ranking, si ya hay sesión de Firebase. */
    fun miJugadorId(context: Context, usuarioId: Long): String? =
        SesionRemota.uidActual(context)?.let { SesionRemota.jugadorId(it, usuarioId) }

    /**
     * Publica una partida terminada y el total actualizado del jugador, en segundo plano.
     * Sin conexión, Firestore guarda las escrituras y las manda cuando vuelve la red.
     */
    fun publicarPartida(context: Context, jugador: JugadorPublicado, puntos: Int, modo: String, categoriaId: Int) {
        if (!disponible(context)) return
        alcance.launch {
            try {
                val uid = SesionRemota.uid()
                val id = SesionRemota.jugadorId(uid, jugador.usuarioId)
                val db = FirebaseFirestore.getInstance()

                escribirJugador(db, uid, id, jugador)
                db.collection(PARTIDAS).add(
                    mapOf(
                        "uid" to uid,
                        "jugadorId" to id,
                        "nombre" to jugador.nombre,
                        "puntos" to puntos,
                        "modo" to modo,
                        "categoriaId" to categoriaId,
                        "fecha" to FieldValue.serverTimestamp()
                    )
                )
            } catch (e: Exception) {
                // Sin red la primera vez no hay sesión: esta partida no llega al ranking por período,
                // pero el total del jugador se vuelve a publicar en la próxima.
                Log.w(TAG, "No se pudo publicar la partida", e)
            }
        }
    }

    /** Publica el total del jugador sin partida nueva: se usa al abrir el ranking. */
    fun sincronizarJugador(context: Context, jugador: JugadorPublicado) {
        if (!disponible(context)) return
        alcance.launch {
            try {
                val uid = SesionRemota.uid()
                escribirJugador(FirebaseFirestore.getInstance(), uid, SesionRemota.jugadorId(uid, jugador.usuarioId), jugador)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo sincronizar el jugador", e)
            }
        }
    }

    private fun escribirJugador(db: FirebaseFirestore, uid: String, id: String, jugador: JugadorPublicado) {
        db.collection(JUGADORES).document(id).set(
            mapOf(
                "uid" to uid,
                "nombre" to jugador.nombre,
                "puntosTotales" to jugador.puntosTotales,
                "nivel" to jugador.nivel,
                "actualizado" to FieldValue.serverTimestamp()
            )
        )
    }

    suspend fun obtenerRanking(periodo: Periodo, ahora: ZonedDateTime = ZonedDateTime.now()): List<FilaRanking> {
        val db = FirebaseFirestore.getInstance()
        val desde = Ranking.inicioDelPeriodo(periodo, ahora)

        if (desde == null) {
            val documentos = db.collection(JUGADORES)
                .orderBy("puntosTotales", Query.Direction.DESCENDING)
                .limit(Ranking.TAMANO.toLong())
                .get().await().documents
            return Ranking.ordenar(
                documentos.map {
                    FilaRanking(it.id, it.getString("nombre") ?: "?", (it.getLong("puntosTotales") ?: 0L).toInt())
                }
            )
        }

        val documentos = db.collection(PARTIDAS)
            .whereGreaterThanOrEqualTo("fecha", Timestamp(Date(desde)))
            .orderBy("fecha")
            .limit(MAX_PARTIDAS_POR_CONSULTA)
            .get().await().documents

        return Ranking.sumarPorJugador(
            documentos.mapNotNull {
                PuntajeDePartida(
                    jugadorId = it.getString("jugadorId") ?: return@mapNotNull null,
                    nombre = it.getString("nombre") ?: "?",
                    puntos = (it.getLong("puntos") ?: 0L).toInt()
                )
            }
        )
    }
}
