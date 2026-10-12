package com.calypso.quizzly

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.calypso.quizzly.util.Sonidos
import com.calypso.quizzly.util.alRecibir
import com.calypso.quizzly.viewmodel.EfectoAcceso
import com.calypso.quizzly.viewmodel.RegistroViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

/** Pantalla de registro de usuario (RF-01). Las validaciones y el alta están en [RegistroViewModel]. */
class RegistroActivity : AppCompatActivity() {

    private val viewModel: RegistroViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registro)

        val etUsuario = findViewById<TextInputEditText>(R.id.etUsuario)
        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val btnRegistrar = findViewById<MaterialButton>(R.id.btnRegistrar)

        btnRegistrar.setOnClickListener {
            Sonidos.toque(this)
            viewModel.registrar(
                nombre = etUsuario.text?.toString()?.trim().orEmpty(),
                correo = etEmail.text?.toString()?.trim().orEmpty(),
                contrasena = etPassword.text?.toString().orEmpty()
            )
        }

        findViewById<TextView>(R.id.tvVolverLogin).setOnClickListener {
            Sonidos.toque(this)
            finish()
        }

        viewModel.ocupado.observe(this) { btnRegistrar.isEnabled = !it }
        alRecibir(viewModel.efectos) { efecto ->
            when (efecto) {
                is EfectoAcceso.Aviso -> Toast.makeText(this, efecto.mensaje, Toast.LENGTH_SHORT).show()
                EfectoAcceso.Entrar -> {
                    startActivity(Intent(this, HomeActivity::class.java))
                    finishAffinity()
                }
            }
        }
    }
}
