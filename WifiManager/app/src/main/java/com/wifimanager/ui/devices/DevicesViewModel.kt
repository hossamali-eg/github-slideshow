package com.wifimanager.ui.devices

import androidx.lifecycle.*
import com.wifimanager.data.models.ConnectedDevice
import com.wifimanager.data.repository.RouterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val repository: RouterRepository
) : ViewModel() {

    val allDevices = repository.allDevices
    val onlineDevices = repository.onlineDevices
    val blockedDevices = repository.blockedDevices

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _message = MutableLiveData<String>()
    val message: LiveData<String> = _message

    private val _filterMode = MutableLiveData<FilterMode>(FilterMode.ALL)
    val filterMode: LiveData<FilterMode> = _filterMode

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.refreshDevices()
            } catch (e: Exception) {
                _message.value = "فشل تحديث قائمة الأجهزة"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun blockDevice(device: ConnectedDevice) {
        viewModelScope.launch {
            val success = repository.blockDevice(device.macAddress, !device.isBlocked)
            if (success) {
                val action = if (!device.isBlocked) "حجب" else "إلغاء حجب"
                _message.value = "تم $action ${device.customName.ifEmpty { device.hostname }}"
            } else {
                _message.value = "فشل تغيير حالة الجهاز"
            }
        }
    }

    fun setSpeedLimit(mac: String, downloadMbps: Int, uploadMbps: Int) {
        viewModelScope.launch {
            val downloadKbps = downloadMbps * 1024
            val uploadKbps = uploadMbps * 1024
            val success = repository.setSpeedLimit(mac, downloadKbps, uploadKbps)
            _message.value = if (success) {
                if (downloadMbps == 0) "تم إزالة قيود السرعة" else "تم تحديد السرعة: ↓${downloadMbps}Mbps ↑${uploadMbps}Mbps"
            } else "فشل تحديد السرعة"
        }
    }

    fun renameDevice(mac: String, name: String) {
        viewModelScope.launch {
            repository.renameDevice(mac, name)
            _message.value = "تم تغيير الاسم"
        }
    }

    fun setDeviceSchedule(mac: String, fromTime: String, toTime: String) {
        viewModelScope.launch {
            repository.setDeviceSchedule(mac, fromTime, toTime, true)
            _message.value = "تم حفظ الجدول الزمني للجهاز"
        }
    }

    fun setFilter(mode: FilterMode) {
        _filterMode.value = mode
    }

    enum class FilterMode { ALL, ONLINE, OFFLINE, BLOCKED }
}
