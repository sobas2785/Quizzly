package com.calypso.quizzly

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.calypso.quizzly.data.local.seed.PreguntaSeeder
import com.calypso.quizzly.util.Sesion
import com.calypso.quizzly.util.Sonidos
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pantalla de inicio (P1): muestra la marca mientras se carga lo necesario y después va al menú,
 * si hay una sesión vigente, o al login.
 */
class SplashActivity : AppCompatActivity() {

    companion object {
        /** La especificación pide mostrarla al menos 2 segundos, aunque la carga termine antes. */
        private const val MS_MINIMOS = 2_000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Íconos claros en la barra de estado, sobre el fondo violeta.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        setContentView(R.layout.activity_splash)

        // El banco de preguntas se carga en el primer arranque y cada vez que cambia el JSON.
        PreguntaSeeder.sembrarEnSegundoPlano(this)

        // Los efectos cortos se precargan acá para que el primer toque ya suene.
        Sonidos.inicializar(this)

        // Solo con la pantalla visible: si el usuario sale durante la espera, no se abre nada de fondo.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                delay(MS_MINIMOS)
                val destino =
                    if (Sesion.usuarioVigente(this@SplashActivity) != Sesion.SIN_SESION) HomeActivity::class.java
                    else MainActivity::class.java
                startActivity(Intent(this@SplashActivity, destino))
                finish()
            }
        }
    }
}
