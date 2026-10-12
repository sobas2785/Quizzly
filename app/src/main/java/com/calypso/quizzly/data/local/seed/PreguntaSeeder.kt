package com.calypso.quizzly.data.local.seed

import android.content.Context
import android.util.Log
import com.calypso.quizzly.data.local.entity.Pregunta
import com.calypso.quizzly.data.repository.PreguntaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray

/**
 * Carga el banco de preguntas desde assets/preguntas.json.
 * Para ampliar el banco alcanza con agregar objetos al JSON: no hay que tocar código.
 *
 * Se guarda una huella del archivo. Si cambia (por ejemplo, porque una actualización de la app
 * trae preguntas nuevas), la tabla se recarga entera, también en celulares que ya tenían la app.
 */
object PreguntaSeeder {

    private const val ARCHIVO = "preguntas.json"
    private const val PREFS = "quizzly_banco"
    private const val CLAVE_HUELLA = "huellaJson"
    private const val TAG = "PreguntaSeeder"

    // Con sesión iniciada, MainActivity se cierra enseguida: la carga no puede depender de su ciclo de vida.
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun sembrarEnSegundoPlano(context: Context) {
        val app = context.applicationContext
        alcance.launch {
            try {
                sembrarSiHaceFalta(app)
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo cargar el banco de preguntas", e)
            }
        }
    }

    private suspend fun sembrarSiHaceFalta(context: Context) {
        val json = context.assets.open(ARCHIVO).bufferedReader().use { it.readText() }
        val huella = json.hashCode()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val repo = PreguntaRepository(context)

        if (prefs.getInt(CLAVE_HUELLA, 0) == huella && repo.contarPreguntas() > 0) return

        repo.reemplazarTodas(parsear(json))
        prefs.edit().putInt(CLAVE_HUELLA, huella).apply()
    }

    private fun parsear(json: String): List<Pregunta> {
        val array = JSONArray(json)

        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            val opciones = obj.getJSONArray("opciones")
            Pregunta(
                categoriaId = obj.getInt("categoriaId"),
                texto = obj.getString("texto"),
                opcionA = opciones.getString(0),
                opcionB = opciones.getString(1),
                opcionC = opciones.getString(2),
                opcionD = opciones.getString(3),
                respuesta = obj.getString("respuesta"),
                dificultad = obj.getString("dificultad")
            )
        }
    }
}
