package com.wifimanager.ui.dashboard

import androidx.lifecycle.*
import com.wifimanager.data.models.NetworkStats
import com.wifimanager.data.models.RouterStatus
import com.wifimanager.data.repository.RouterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: RouterRepository
) : ViewModel() {

    private val _networkStats = MutableLiveData<NetworkStats>()
    val networkStats: LiveData<NetworkStats> = _networkStats

    private val _routerStatus = MutableLiveData<RouterStatus>()
    val routerStatus: LiveData<RouterStatus> = _routerStatus

    private val _internetEnabled = MutableLiveData<Boolean>(true)
    val internetEnabled: LiveData<Boolean> = _internetEnabled

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _message = MutableLiveData<String>()
    val message: LiveData<String> = _message

    val onlineDevices = repository.onlineDevices
    val allDevices = repository.allDevices

    private var monitorJob: kotlinx.coroutines.Job? = null

    init {
        startMonitoring()
    }

    fun startMonitoring() {
        monitorJob?.cancel()
        monitorJob = viewModelScope.launch {
            while (isActive) {
                refreshStats()
                delay(5000L) // refresh every 5 seconds
            }
        }
    }

    fun stopMonitoring() {
        monitorJob?.cancel()
    }

    private suspend fun refreshStats() {
        try {
            val stats = repository.getNetworkStats()
            _networkStats.postValue(stats)
            _internetEnabled.postValue(stats.isInternetEnabled)
        } catch (e: Exception) {
            // Keep existing stats on error
        }
    }

    fun refreshDevices() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.refreshDevices()
            } catch (e: Exception) {
                _message.value = "فشل تحديث الأجهزة"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleInternet() {
        viewModelScope.launch {
            val current = _internetEnabled.value ?: true
            _isLoading.value = true
            try {
                val success = repository.setInternetEnabled(!current)
                if (success) {
                    _internetEnabled.value = !current
                    _message.value = if (!current) "تم تشغيل الإنترنت" else "تم إيقاف الإنترنت"
                } else {
                    _message.value = "فشل تغيير حالة الإنترنت"
                }
            } catch (e: Exception) {
                _message.value = "خطأ: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun disableInternetFor(minutes: Int) {
        viewModelScope.launch {
            val success = repository.setInternetEnabled(false)
            if (success) {
                _internetEnabled.value = false
                _message.value = "سيتم إيقاف الإنترنت لمدة $minutes دقيقة"
                delay(minutes * 60 * 1000L)
                repository.setInternetEnabled(true)
                _internetEnabled.value = true
                _message.value = "تم إعادة تشغيل الإنترنت"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        monitorJob?.cancel()
    }
}
