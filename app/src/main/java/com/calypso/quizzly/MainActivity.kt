package com.calypso.quizzly

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.calypso.quizzly.data.local.seed.PreguntaSeeder
import com.calypso.quizzly.util.Sesion
import com.calypso.quizzly.util.Sonidos
import com.calypso.quizzly.util.alRecibir
import com.calypso.quizzly.viewmodel.EfectoAcceso
import com.calypso.quizzly.viewmodel.LoginViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

/** Pantalla de inicio de sesión (RF-02). La autenticación está en [LoginViewModel]. */
class MainActivity : AppCompatActivity() {

    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // El banco de preguntas se carga en el primer arranque y cada vez que cambia el JSON.
        PreguntaSeeder.sembrarEnSegundoPlano(this)

        // Los efectos cortos se precargan acá para que el primer toque ya suene.
        Sonidos.inicializar(this)

        if (Sesion.usuarioId(this) != Sesion.SIN_SESION) {
            irAlMenu()
            return
        }

        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val btnLogin = findViewById<MaterialButton>(R.id.btnLogin)

        btnLogin.setOnClickListener {
            Sonidos.toque(this)
            viewModel.ingresar(
                correo = etEmail.text?.toString()?.trim().orEmpty(),
                contrasena = etPassword.text?.toString().orEmpty()
            )
        }

        findViewById<TextView>(R.id.tvRegister).setOnClickListener {
            Sonidos.toque(this)
            startActivity(Intent(this, RegistroActivity::class.java))
        }

        viewModel.ocupado.observe(this) { btnLogin.isEnabled = !it }
        alRecibir(viewModel.efectos) { efecto ->
            when (efecto) {
                is EfectoAcceso.Aviso -> Toast.makeText(this, efecto.mensaje, Toast.LENGTH_SHORT).show()
                EfectoAcceso.Entrar -> irAlMenu()
            }
        }
    }

    private fun irAlMenu() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}
