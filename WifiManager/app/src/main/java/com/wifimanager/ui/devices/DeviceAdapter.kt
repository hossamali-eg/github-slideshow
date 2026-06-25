package com.wifimanager.ui.devices

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.wifimanager.R
import com.wifimanager.data.models.ConnectedDevice
import com.wifimanager.data.models.DeviceType
import com.wifimanager.databinding.ItemDeviceBinding

class DeviceAdapter(
    private val onBlock: (ConnectedDevice) -> Unit,
    private val onSpeedLimit: (ConnectedDevice) -> Unit,
    private val onSchedule: (ConnectedDevice) -> Unit
) : ListAdapter<ConnectedDevice, DeviceAdapter.DeviceViewHolder>(DIFF_CALLBACK) {

    inner class DeviceViewHolder(private val binding: ItemDeviceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(device: ConnectedDevice) {
            val displayName = device.customName.ifEmpty { device.hostname.ifEmpty { device.macAddress } }
            binding.tvDeviceName.text = displayName
            binding.tvDeviceIp.text = device.ipAddress.ifEmpty { device.macAddress }
            binding.tvDeviceIcon.text = getDeviceIcon(device.deviceType)

            // Status
            when {
                device.isBlocked -> {
                    binding.tvStatus.text = "محجوب"
                    binding.tvStatus.setTextColor(Color.parseColor("#EF4444"))
                    binding.tvStatus.setBackgroundResource(R.drawable.bg_status_badge_red)
                    binding.btnBlock.text = "إلغاء الحجب"
                }
                device.isOnline -> {
                    binding.tvStatus.text = "متصل"
                    binding.tvStatus.setTextColor(Color.parseColor("#22C55E"))
                    binding.tvStatus.setBackgroundResource(R.drawable.bg_status_badge_green)
                    binding.btnBlock.text = "حجب"
                }
                else -> {
                    binding.tvStatus.text = "غير متصل"
                    binding.tvStatus.setTextColor(Color.parseColor("#8B92A8"))
                    binding.tvStatus.setBackgroundResource(R.drawable.bg_status_badge)
                    binding.btnBlock.text = "حجب"
                }
            }

            // Speed
            if (device.isOnline && !device.isBlocked) {
                binding.speedRow.visibility = View.VISIBLE
                binding.tvDownSpeed.text = "%.1f Mbps".format(device.downloadSpeed)
                binding.tvUpSpeed.text = "%.1f Mbps".format(device.uploadSpeed)
            } else {
                binding.speedRow.visibility = View.GONE
            }

            // Speed limit badge
            if (device.downloadSpeedLimit > 0) {
                binding.tvSpeedLimit.visibility = View.VISIBLE
                binding.tvSpeedLimit.text = "محدود: ${device.downloadSpeedLimit / 1024}Mbps"
            } else {
                binding.tvSpeedLimit.visibility = View.GONE
            }

            binding.btnBlock.setOnClickListener { onBlock(device) }
            binding.btnSpeedLimit.setOnClickListener { onSpeedLimit(device) }
            binding.btnSchedule.setOnClickListener { onSchedule(device) }
        }

        private fun getDeviceIcon(type: DeviceType): String = when (type) {
            DeviceType.PHONE -> "📱"
            DeviceType.TABLET -> "📱"
            DeviceType.LAPTOP -> "💻"
            DeviceType.DESKTOP -> "🖥️"
            DeviceType.TV -> "📺"
            DeviceType.GAME_CONSOLE -> "🎮"
            DeviceType.SMART_HOME -> "🏠"
            DeviceType.CAMERA -> "📷"
            else -> "📡"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ConnectedDevice>() {
            override fun areItemsTheSame(old: ConnectedDevice, new: ConnectedDevice) =
                old.macAddress == new.macAddress
            override fun areContentsTheSame(old: ConnectedDevice, new: ConnectedDevice) =
                old == new
        }
    }
}
