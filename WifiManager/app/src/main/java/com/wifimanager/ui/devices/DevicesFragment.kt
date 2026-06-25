package com.wifimanager.ui.devices

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.wifimanager.data.models.ConnectedDevice
import com.wifimanager.databinding.FragmentDevicesBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DevicesFragment : Fragment() {

    private var _binding: FragmentDevicesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DevicesViewModel by viewModels()
    private lateinit var adapter: DeviceAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDevicesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAdapter()
        setupObservers()
        setupFilterChips()
        setupSwipeRefresh()
        viewModel.refresh()
    }

    private fun setupAdapter() {
        adapter = DeviceAdapter(
            onBlock = { showBlockDialog(it) },
            onSpeedLimit = { showSpeedLimitDialog(it) },
            onSchedule = { showScheduleDialog(it) }
        )
        binding.rvDevices.adapter = adapter
        binding.rvDevices.layoutManager =
            androidx.recyclerview.widget.LinearLayoutManager(requireContext())
    }

    private fun setupObservers() {
        viewModel.allDevices.observe(viewLifecycleOwner) { devices ->
            applyFilter(devices)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            binding.swipeRefresh.isRefreshing = loading
        }

        viewModel.message.observe(viewLifecycleOwner) { msg ->
            if (!msg.isNullOrEmpty()) {
                Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
            }
        }

        viewModel.filterMode.observe(viewLifecycleOwner) {
            viewModel.allDevices.value?.let { devices -> applyFilter(devices) }
        }
    }

    private fun applyFilter(devices: List<ConnectedDevice>) {
        val filtered = when (viewModel.filterMode.value) {
            DevicesViewModel.FilterMode.ONLINE -> devices.filter { it.isOnline && !it.isBlocked }
            DevicesViewModel.FilterMode.OFFLINE -> devices.filter { !it.isOnline }
            DevicesViewModel.FilterMode.BLOCKED -> devices.filter { it.isBlocked }
            else -> devices
        }
        adapter.submitList(filtered)
        binding.emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.rvDevices.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun setupFilterChips() {
        binding.chipAll.setOnClickListener { viewModel.setFilter(DevicesViewModel.FilterMode.ALL) }
        binding.chipOnline.setOnClickListener { viewModel.setFilter(DevicesViewModel.FilterMode.ONLINE) }
        binding.chipOffline.setOnClickListener { viewModel.setFilter(DevicesViewModel.FilterMode.OFFLINE) }
        binding.chipBlocked.setOnClickListener { viewModel.setFilter(DevicesViewModel.FilterMode.BLOCKED) }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }
    }

    private fun showBlockDialog(device: ConnectedDevice) {
        val name = device.customName.ifEmpty { device.hostname }
        val action = if (device.isBlocked) "إلغاء حجب" else "حجب"
        AlertDialog.Builder(requireContext())
            .setTitle("$action الجهاز")
            .setMessage("هل تريد $action $name ؟")
            .setPositiveButton(action) { _, _ -> viewModel.blockDevice(device) }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showSpeedLimitDialog(device: ConnectedDevice) {
        val name = device.customName.ifEmpty { device.hostname }
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
        }

        val tvDown = TextView(requireContext()).apply { text = "سرعة التنزيل: 10 Mbps" }
        val seekDown = SeekBar(requireContext()).apply {
            max = 100
            progress = if (device.downloadSpeedLimit > 0) device.downloadSpeedLimit / 1024 else 10
        }
        val tvUp = TextView(requireContext()).apply {
            text = "سرعة الرفع: 5 Mbps"
            setPadding(0, 16, 0, 0)
        }
        val seekUp = SeekBar(requireContext()).apply {
            max = 50
            progress = if (device.uploadSpeedLimit > 0) device.uploadSpeedLimit / 1024 else 5
        }

        seekDown.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                tvDown.text = if (p == 0) "سرعة التنزيل: بدون حد" else "سرعة التنزيل: $p Mbps"
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        seekUp.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                tvUp.text = if (p == 0) "سرعة الرفع: بدون حد" else "سرعة الرفع: $p Mbps"
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        layout.addView(tvDown)
        layout.addView(seekDown)
        layout.addView(tvUp)
        layout.addView(seekUp)

        AlertDialog.Builder(requireContext())
            .setTitle("تحديد سرعة: $name")
            .setView(layout)
            .setPositiveButton("تطبيق") { _, _ ->
                viewModel.setSpeedLimit(device.macAddress, seekDown.progress, seekUp.progress)
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showScheduleDialog(device: ConnectedDevice) {
        val name = device.customName.ifEmpty { device.hostname }
        var blockFrom = ""
        var blockTo = ""

        fun pickTime(label: String, onPicked: (String) -> Unit) {
            MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_12H)
                .setTitleText(label)
                .build()
                .apply {
                    addOnPositiveButtonClickListener {
                        onPicked("%02d:%02d".format(hour, minute))
                    }
                    show(this@DevicesFragment.parentFragmentManager, "timePicker")
                }
        }

        pickTime("وقت الحجب") { from ->
            blockFrom = from
            pickTime("وقت إلغاء الحجب") { to ->
                blockTo = to
                AlertDialog.Builder(requireContext())
                    .setTitle("جدولة: $name")
                    .setMessage("سيتم حجب الجهاز من $blockFrom حتى $blockTo يومياً")
                    .setPositiveButton("حفظ") { _, _ ->
                        viewModel.setDeviceSchedule(device.macAddress, blockFrom, blockTo)
                    }
                    .setNegativeButton("إلغاء", null)
                    .show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
