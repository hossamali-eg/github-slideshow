package com.wifimanager.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
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

    // Launcher for the visible WebView login screen
    private val webViewLoginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val cookies = result.data?.getStringExtra(RouterWebViewActivity.RESULT_COOKIES) ?: ""
            val stok   = result.data?.getStringExtra(RouterWebViewActivity.RESULT_STOK)    ?: ""
            val ip       = binding.etRouterIp.text.toString().trim()
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString()
            val config = RouterConfig(
                ipAddress  = ip,
                username   = username,
                password   = password,
                routerType = RouterType.ZTE
            )
            viewModel.onWebViewLoginSuccess(config, cookies, stok)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRouterTypeDropdown()
        setupSavedRoutersList()
        setupObservers()
        setupClickListeners()
        preselectZTE()
    }

    private fun setupRouterTypeDropdown() {
        val types = resources.getStringArray(R.array.router_types)
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, types)
        binding.spinnerRouterType.setAdapter(adapter)
        binding.spinnerRouterType.setText(types[0], false)

        binding.spinnerRouterType.setOnItemClickListener { _, _, position, _ ->
            when (routerTypeValues.getOrNull(position)) {
                RouterType.ZTE -> {
                    if (binding.etRouterIp.text.isNullOrBlank()) binding.etRouterIp.setText("192.168.1.1")
                    if (binding.etUsername.text.isNullOrBlank()) binding.etUsername.setText("admin")
                }
                RouterType.TP_LINK -> {
                    if (binding.etRouterIp.text.isNullOrBlank()) binding.etRouterIp.setText("192.168.0.1")
                    if (binding.etUsername.text.isNullOrBlank()) binding.etUsername.setText("admin")
                }
                RouterType.HUAWEI -> {
                    if (binding.etRouterIp.text.isNullOrBlank()) binding.etRouterIp.setText("192.168.1.1")
                    if (binding.etUsername.text.isNullOrBlank()) binding.etUsername.setText("admin")
                }
                else -> {}
            }
        }
    }

    private fun preselectZTE() {
        val types = resources.getStringArray(R.array.router_types)
        val idx = types.indexOfFirst { it.contains("ZTE", ignoreCase = true) }
        if (idx >= 0) binding.spinnerRouterType.setText(types[idx], false)
        binding.etRouterIp.setText("192.168.1.1")
        binding.etUsername.setText("admin")
    }

    private fun setupSavedRoutersList() {
        val adapter = SavedRouterAdapter(
            onConnect = { viewModel.connectSaved(it) },
            onDelete  = { viewModel.deleteRouter(it) }
        )
        binding.rvSavedRouters.adapter = adapter
        binding.rvSavedRouters.layoutManager =
            androidx.recyclerview.widget.LinearLayoutManager(this)
        viewModel.savedRouters.observe(this) { adapter.submitList(it) }
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(this) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            binding.btnConnect.isEnabled   = !loading
        }

        viewModel.loginState.observe(this) { state ->
            when (state) {
                is LoginViewModel.LoginState.Success -> {
                    viewModel.saveAndConnect(state.config)
                    navigateToDashboard()
                }
                is LoginViewModel.LoginState.Error -> showError(state.message)
                is LoginViewModel.LoginState.ConnectionSuccess ->
                    showMessage("تم الاتصال بالراوتر!")
                else -> {}
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnConnect.setOnClickListener {
            val ip       = binding.etRouterIp.text.toString().trim()
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString()

            val selectedLabel = binding.spinnerRouterType.text.toString()
            val types = resources.getStringArray(R.array.router_types)
            val idx = types.indexOfFirst { it == selectedLabel }.let { if (it < 0) 0 else it }
            val routerType = routerTypeValues.getOrElse(idx) { RouterType.GENERIC }

            if (ip.isEmpty()) {
                binding.tilRouterIp.error = "أدخل IP الراوتر"
                return@setOnClickListener
            }
            binding.tilRouterIp.error = null

            if (routerType == RouterType.ZTE) {
                // Open visible WebView — user sees and submits the real router login page
                val intent = Intent(this, RouterWebViewActivity::class.java).apply {
                    putExtra(RouterWebViewActivity.EXTRA_IP,       ip)
                    putExtra(RouterWebViewActivity.EXTRA_USERNAME, username)
                    putExtra(RouterWebViewActivity.EXTRA_PASSWORD, password)
                }
                webViewLoginLauncher.launch(intent)
            } else {
                viewModel.login(ip, username, password, routerType)
            }
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
