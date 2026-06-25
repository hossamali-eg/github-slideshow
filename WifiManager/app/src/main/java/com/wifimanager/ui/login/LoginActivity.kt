package com.wifimanager.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.wifimanager.R
import com.wifimanager.data.models.RouterConfig
import com.wifimanager.data.models.RouterType
import com.wifimanager.databinding.ActivityLoginBinding
import com.wifimanager.ui.dashboard.DashboardActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()
    private val routerTypeValues = RouterType.values()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRouterTypeDropdown()
        setupSavedRoutersList()
        setupObservers()
        setupClickListeners()
    }

    private fun setupRouterTypeDropdown() {
        val types = resources.getStringArray(R.array.router_types)
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, types)
        binding.spinnerRouterType.setAdapter(adapter)
        binding.spinnerRouterType.setText(types[0], false)
    }

    private fun setupSavedRoutersList() {
        val adapter = SavedRouterAdapter(
            onConnect = { viewModel.connectSaved(it) },
            onDelete = { viewModel.deleteRouter(it) }
        )
        binding.rvSavedRouters.adapter = adapter
        binding.rvSavedRouters.layoutManager =
            androidx.recyclerview.widget.LinearLayoutManager(this)

        viewModel.savedRouters.observe(this) { routers ->
            adapter.submitList(routers)
        }
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(this) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            binding.btnConnect.isEnabled = !loading
        }

        viewModel.loginState.observe(this) { state ->
            when (state) {
                is LoginViewModel.LoginState.Success -> {
                    viewModel.saveAndConnect(state.config)
                    navigateToDashboard()
                }
                is LoginViewModel.LoginState.Error -> {
                    showError(state.message)
                }
                is LoginViewModel.LoginState.ConnectionSuccess -> {
                    showMessage("تم الاتصال بالراوتر! جاري تسجيل الدخول...")
                }
                else -> {}
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnConnect.setOnClickListener {
            val ip = binding.etRouterIp.text.toString().trim()
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString()
            val typeIndex = routerTypeValues.indexOfFirst {
                it.name == binding.spinnerRouterType.text.toString()
            }.let { if (it == -1) 0 else it }

            if (ip.isEmpty()) {
                binding.tilRouterIp.error = "أدخل IP الراوتر"
                return@setOnClickListener
            }

            binding.tilRouterIp.error = null
            viewModel.login(ip, username, password, routerTypeValues[typeIndex])
        }
    }

    private fun navigateToDashboard() {
        startActivity(Intent(this, DashboardActivity::class.java))
        finish()
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(getColor(R.color.red))
            .show()
    }

    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }
}
