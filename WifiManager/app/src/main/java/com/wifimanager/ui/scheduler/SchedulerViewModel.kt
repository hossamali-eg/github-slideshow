package com.wifimanager.ui.scheduler

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifimanager.data.models.ScheduleRule
import com.wifimanager.data.repository.RouterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SchedulerViewModel @Inject constructor(
    private val repository: RouterRepository
) : ViewModel() {

    val rules = repository.allScheduleRules

    private val _message = MutableLiveData<String>()
    val message = _message

    fun addRule(rule: ScheduleRule) {
        viewModelScope.launch {
            repository.saveScheduleRule(rule)
            _message.value = "تم حفظ القاعدة: ${rule.name}"
        }
    }

    fun toggleRule(id: Int, enabled: Boolean) {
        viewModelScope.launch {
            repository.toggleScheduleRule(id, enabled)
        }
    }

    fun deleteRule(rule: ScheduleRule) {
        viewModelScope.launch {
            repository.deleteScheduleRule(rule)
            _message.value = "تم حذف القاعدة"
        }
    }
}
