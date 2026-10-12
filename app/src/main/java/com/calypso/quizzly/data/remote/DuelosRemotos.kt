package com.calypso.quizzly.data.remote

import com.calypso.quizzly.game.DueloOnline
import com.calypso.quizzly.game.EstadoDuelo
import com.calypso.quizzly.game.ReglasDueloOnline
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Duelos online por turnos, en la colección "duelos" de Firestore.
 *
 * Cada duelo es un solo documento cuyo id es el código de invitación. Los dos celulares lo escuchan:
 * cuando uno juega su turno, el documento cambia y el otro lo ve al instante si tiene la app abierta.
 * Crear, unirse y jugar van en transacciones, para que dos celulares no pisen el mismo duelo, y las
 * reglas de firestore.rules vuelven a controlar lo mismo del lado del servidor.
 */
object DuelosRemotos {

    private const val DUELOS = "duelos"
    private const val INTENTOS_DE_CODIGO = 5

    sealed interface ResultadoUnirse {
        data class Unido(val duelo: DueloOnline) : ResultadoUnirse
        data object NoExiste : ResultadoUnirse
        data object YaEmpezo : ResultadoUnirse

        /** El duelo lo creó este mismo celular. */
        data object EsPropio : ResultadoUnirse
    }

    private fun duelos() = FirebaseFirestore.getInstance().collection(DUELOS)

    /** Crea un duelo que queda esperando rival y devuelve su código de invitación adentro. */
    suspend fun crear(usuarioId: Long, nombre: String): DueloOnline {
        val uid = SesionRemota.uid()

        repeat(INTENTOS_DE_CODIGO) {
            val ref = duelos().document(ReglasDueloOnline.nuevoCodigo())
            val creado = FirebaseFirestore.getInstance().runTransaction { transaccion ->
                // Si justo ese código ya existe, se prueba con otro.
                if (transaccion.get(ref).exists()) return@runTransaction false

                transaccion.set(
                    ref,
                    mapOf(
                        "codigo" to ref.id,
                        "uids" to listOf(uid),
                        "uid1" to uid,
                        "jugadorId1" to SesionRemota.jugadorId(uid, usuarioId),
                        "nombre1" to nombre,
                        "puntos1" to 0,
                        "correctas1" to 0,
                        "puntos2" to 0,
                        "correctas2" to 0,
                        "estado" to EstadoDuelo.ESPERANDO.clave,
                        "turno" to 1,
                        "turnosJugados" to 0,
                        "ultimaCategoriaId" to 0,
                        "creado" to FieldValue.serverTimestamp(),
                        "actualizado" to FieldValue.serverTimestamp()
                    )
                )
                true
            }.await()

            if (creado) return checkNotNull(ref.get().await().aDuelo())
        }
        error("No se pudo generar un código de duelo libre")
    }

    /** Suma a este jugador como rival del duelo con ese código, si todavía está esperando a alguien. */
    suspend fun unirse(codigo: String, usuarioId: Long, nombre: String): ResultadoUnirse {
        val uid = SesionRemota.uid()
        val ref = duelos().document(codigo)

        val rechazo = FirebaseFirestore.getInstance().runTransaction<ResultadoUnirse?> { transaccion ->
            val doc = transaccion.get(ref)
            when {
                !doc.exists() -> ResultadoUnirse.NoExiste
                doc.getString("uid1") == uid -> ResultadoUnirse.EsPropio
                doc.getString("estado") != EstadoDuelo.ESPERANDO.clave -> ResultadoUnirse.YaEmpezo
                else -> {
                    transaccion.update(
                        ref,
                        mapOf(
                            "uid2" to uid,
                            "jugadorId2" to SesionRemota.jugadorId(uid, usuarioId),
                            "nombre2" to nombre,
                            "uids" to listOf(doc.getString("uid1"), uid),
                            "estado" to EstadoDuelo.EN_CURSO.clave,
                            "actualizado" to FieldValue.serverTimestamp()
                        )
                    )
                    null
                }
            }
        }.await()

        return rechazo ?: ResultadoUnirse.Unido(checkNotNull(ref.get().await().aDuelo()))
    }

    /**
     * Suma el turno de ese jugador y le pasa el turno al rival; el sexto turno cierra el duelo.
     * Si el turno ya estaba anotado (un reintento después de un corte de red), no lo suma dos veces.
     */
    suspend fun jugarTurno(codigo: String, numero: Int, puntos: Int, acerto: Boolean, categoriaId: Int): DueloOnline {
        val ref = duelos().document(codigo)

        FirebaseFirestore.getInstance().runTransaction { transaccion ->
            val duelo = transaccion.get(ref).aDuelo()

            if (duelo != null && duelo.estado == EstadoDuelo.EN_CURSO && duelo.turno == numero) {
                transaccion.update(
                    ref,
                    mapOf(
                        "puntos$numero" to duelo.puntosDe(numero) + puntos,
                        "correctas$numero" to duelo.correctasDe(numero) + if (acerto) 1 else 0,
                        "turno" to 3 - numero,
                        "turnosJugados" to duelo.turnosJugados + 1,
                        "estado" to ReglasDueloOnline.estadoTrasElTurno(duelo.turnosJugados).clave,
                        "ultimaCategoriaId" to categoriaId,
                        "actualizado" to FieldValue.serverTimestamp()
                    )
                )
            }
            null
        }.await()

        return checkNotNull(ref.get().await().aDuelo())
    }

    /** Borra un duelo propio que todavía está esperando rival. */
    suspend fun cancelar(codigo: String) {
        duelos().document(codigo).delete().await()
    }

    /** Los duelos donde participa este celular, actualizados en vivo mientras alguien los escuche. */
    fun escucharMisDuelos(uid: String): Flow<List<DueloOnline>> = callbackFlow {
        val registro = duelos().whereArrayContains("uids", uid)
            .addSnapshotListener { cambios, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(cambios?.documents?.mapNotNull { it.aDuelo() }.orEmpty())
            }
        awaitClose { registro.remove() }
    }

    private fun DocumentSnapshot.aDuelo(): DueloOnline? {
        if (!exists()) return null

        return DueloOnline(
            codigo = id,
            estado = EstadoDuelo.deClave(getString("estado")),
            turno = entero("turno", 1),
            turnosJugados = entero("turnosJugados"),
            jugadorId1 = getString("jugadorId1") ?: return null,
            nombre1 = getString("nombre1") ?: "?",
            puntos1 = entero("puntos1"),
            correctas1 = entero("correctas1"),
            jugadorId2 = getString("jugadorId2"),
            nombre2 = getString("nombre2"),
            puntos2 = entero("puntos2"),
            correctas2 = entero("correctas2"),
            ultimaCategoriaId = entero("ultimaCategoriaId"),
            // Mientras la escritura viaja al servidor, la hora todavía no llegó: se estima con la del celular.
            actualizado = getTimestamp("actualizado", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?.toDate()?.time ?: 0L
        )
    }

    private fun DocumentSnapshot.entero(campo: String, porDefecto: Int = 0): Int =
        getLong(campo)?.toInt() ?: porDefecto
}
