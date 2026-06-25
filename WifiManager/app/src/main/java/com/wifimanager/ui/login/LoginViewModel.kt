package com.wifimanager.ui.login

import androidx.lifecycle.*
import com.wifimanager.data.models.RouterConfig
import com.wifimanager.data.models.RouterStatus
import com.wifimanager.data.models.RouterType
import com.wifimanager.data.repository.RouterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: RouterRepository
) : ViewModel() {

    val savedRouters = repository.allRouters

    private val _loginState = MutableLiveData<LoginState>()
    val loginState: LiveData<LoginState> = _loginState

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    fun testConnection(ip: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val status = repository.testConnection(ip)
            _loginState.value = if (status.isConnected)
                LoginState.ConnectionSuccess
            else
                LoginState.Error("لا يمكن الوصول إلى $ip")
            _isLoading.value = false
        }
    }

    fun login(ip: String, username: String, password: String, routerType: RouterType) {
        viewModelScope.launch {
            _isLoading.value = true
            _loginState.value = LoginState.Loading

            val config = RouterConfig(
                ipAddress = ip,
                username = username,
                password = password,
                routerType = routerType
            )

            val status = repository.connect(config)
            _loginState.value = when {
                status.isAuthenticated -> LoginState.Success(config)
                status.isConnected -> LoginState.Error("اسم المستخدم أو كلمة المرور غير صحيحة")
                else -> LoginState.Error(status.errorMessage.ifEmpty { "لا يمكن الاتصال بالراوتر" })
            }
            _isLoading.value = false
        }
    }

    fun saveAndConnect(config: RouterConfig) {
        viewModelScope.launch {
            val id = repository.saveRouterConfig(config)
            repository.setActiveRouter(id.toInt())
        }
    }

    fun connectSaved(config: RouterConfig) {
        viewModelScope.launch {
            _isLoading.value = true
            _loginState.value = LoginState.Loading
            val status = repository.connect(config)
            _loginState.value = if (status.isAuthenticated) {
                repository.setActiveRouter(config.id)
                LoginState.Success(config)
            } else {
                LoginState.Error(status.errorMessage.ifEmpty { "فشل الاتصال" })
            }
            _isLoading.value = false
        }
    }

    fun deleteRouter(config: RouterConfig) {
        viewModelScope.launch {
            repository.deleteRouter(config)
        }
    }

    sealed class LoginState {
        object Loading : LoginState()
        object ConnectionSuccess : LoginState()
        data class Success(val config: RouterConfig) : LoginState()
        data class Error(val message: String) : LoginState()
    }
}
