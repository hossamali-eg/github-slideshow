package com.wifimanager.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.wifimanager.R
import com.wifimanager.data.models.RouterConfig
import com.wifimanager.data.models.RouterType
import com.wifimanager.databinding.ActivityLoginBinding
import com.wifimanager.ui.dashboard.DashboardActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

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
        // Pre-select ZTE as default (most common router for this user)
        preselectZTE()
    }

    private fun setupRouterTypeDropdown() {
        val types = resources.getStringArray(R.array.router_types)
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, types)
        binding.spinnerRouterType.setAdapter(adapter)
        binding.spinnerRouterType.setText(types[0], false)

        // Auto-fill defaults per router type
        binding.spinnerRouterType.setOnItemClickListener { _, _, position, _ ->
            when (routerTypeValues.getOrNull(position)) {
                RouterType.ZTE -> {
                    if (binding.etRouterIp.text.isNullOrBlank())
                        binding.etRouterIp.setText("192.168.1.1")
                    if (binding.etUsername.text.isNullOrBlank())
                        binding.etUsername.setText("admin")
                }
                RouterType.TP_LINK -> {
                    if (binding.etRouterIp.text.isNullOrBlank())
                        binding.etRouterIp.setText("192.168.0.1")
                    if (binding.etUsername.text.isNullOrBlank())
                        binding.etUsername.setText("admin")
                }
                RouterType.HUAWEI -> {
                    if (binding.etRouterIp.text.isNullOrBlank())
                        binding.etRouterIp.setText("192.168.1.1")
                    if (binding.etUsername.text.isNullOrBlank())
                        binding.etUsername.setText("admin")
                }
                else -> {}
            }
        }
    }

    private fun preselectZTE() {
        val types = resources.getStringArray(R.array.router_types)
        val zteIndex = types.indexOfFirst { it.contains("ZTE", ignoreCase = true) }
        if (zteIndex >= 0) {
            binding.spinnerRouterType.setText(types[zteIndex], false)
        }
        // ZTE H188A default IP
        binding.etRouterIp.setText("192.168.1.1")
        binding.etUsername.setText("admin")
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

            val selectedLabel = binding.spinnerRouterType.text.toString()
            val types = resources.getStringArray(R.array.router_types)
            val selectedIndex = types.indexOfFirst { it == selectedLabel }
                .let { if (it < 0) 0 else it }
            val routerType = routerTypeValues.getOrElse(selectedIndex) { RouterType.GENERIC }

            if (ip.isEmpty()) {
                binding.tilRouterIp.error = "أدخل IP الراوتر"
                return@setOnClickListener
            }

            binding.tilRouterIp.error = null

            if (routerType == RouterType.ZTE) {
                binding.progressBar.visibility = View.VISIBLE
                binding.btnConnect.isEnabled = false
                lifecycleScope.launch {
                    val result = try {
                        WebViewLoginHelper(this@LoginActivity, binding.root as? ViewGroup).login(ip, username, password)
                    } catch (e: Exception) {
                        WebViewLoginHelper.LoginResult(false, errorMessage = e.message ?: "خطأ في الاتصال")
                    }
                    binding.progressBar.visibility = View.GONE
                    binding.btnConnect.isEnabled = true
                    if (result.success) {
                        val config = RouterConfig(ipAddress = ip, username = username, password = password, routerType = routerType)
                        viewModel.onWebViewLoginSuccess(config, result.cookies, result.stok)
                    } else {
                        showError(result.errorMessage.ifEmpty { "اسم المستخدم أو كلمة المرور غير صحيحة" })
                    }
                }
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
