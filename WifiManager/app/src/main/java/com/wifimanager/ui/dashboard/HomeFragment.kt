package com.wifimanager.ui.dashboard

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.NumberPicker
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.wifimanager.R
import com.wifimanager.data.models.ConnectedDevice
import com.wifimanager.databinding.FragmentHomeBinding
import com.wifimanager.ui.devices.DeviceAdapter
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DashboardViewModel by viewModels()
    private lateinit var deviceAdapter: DeviceAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDevicesRecycler()
        setupObservers()
        setupClickListeners()
        viewModel.refreshDevices()
    }

    private fun setupDevicesRecycler() {
        deviceAdapter = DeviceAdapter(
            onBlock = { /* handled in devices fragment */ },
            onSpeedLimit = { /* handled in devices fragment */ },
            onSchedule = { /* handled in devices fragment */ }
        )
        binding.rvRecentDevices.adapter = deviceAdapter
        binding.rvRecentDevices.layoutManager =
            androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        binding.rvRecentDevices.isNestedScrollingEnabled = false
    }

    private fun setupObservers() {
        viewModel.networkStats.observe(viewLifecycleOwner) { stats ->
            binding.tvDownloadSpeed.text = "%.1f".format(stats.totalDownloadSpeed)
            binding.tvUploadSpeed.text = "%.1f".format(stats.totalUploadSpeed)
            binding.tvPing.text = stats.pingMs.toString()
            binding.tvSSID.text = stats.ssid
            binding.tvActiveCount.text = stats.activeDevices.toString()
            binding.tvTotalCount.text = stats.totalDevices.toString()
            binding.tvBlockedCount.text = stats.blockedDevices.toString()
        }

        viewModel.internetEnabled.observe(viewLifecycleOwner) { enabled ->
            binding.switchInternet.isChecked = enabled
            binding.tvInternetStatus.text = if (enabled) "مفعّل" else "موقف"
            binding.tvInternetStatus.setTextColor(
                requireContext().getColor(if (enabled) R.color.green else R.color.red)
            )
        }

        viewModel.onlineDevices.observe(viewLifecycleOwner) { devices ->
            deviceAdapter.submitList(devices.take(3))
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.swipeRefresh.isRefreshing = loading
        }

        viewModel.message.observe(viewLifecycleOwner) { msg ->
            if (!msg.isNullOrEmpty()) {
                Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupClickListeners() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshDevices()
        }

        binding.switchInternet.setOnCheckedChangeListener { _, _ ->
            viewModel.toggleInternet()
        }

        binding.cardTimedOff.setOnClickListener {
            showTimedDisableDialog()
        }

        binding.cardSpeedTest.setOnClickListener {
            showSpeedTestDialog()
        }

        binding.cardBlockAll.setOnClickListener {
            showBlockAllConfirmation()
        }

        binding.cardSchedule.setOnClickListener {
            findNavController().navigate(R.id.scheduleFragment)
        }

        binding.tvViewAllDevices.setOnClickListener {
            findNavController().navigate(R.id.devicesFragment)
        }
    }

    private fun showTimedDisableDialog() {
        val options = arrayOf("15 دقيقة", "30 دقيقة", "1 ساعة", "2 ساعة", "4 ساعات", "مخصص")
        AlertDialog.Builder(requireContext())
            .setTitle("إيقاف الإنترنت مؤقتاً")
            .setItems(options) { _, which ->
                val minutes = when (which) {
                    0 -> 15; 1 -> 30; 2 -> 60; 3 -> 120; 4 -> 240
                    else -> showCustomTimeDialog()
                }
                if (minutes > 0) {
                    viewModel.disableInternetFor(minutes)
                }
            }
            .show()
    }

    private fun showCustomTimeDialog(): Int {
        // Returns 0; handled separately
        val picker = NumberPicker(requireContext()).apply {
            minValue = 1
            maxValue = 480
            value = 30
        }
        AlertDialog.Builder(requireContext())
            .setTitle("مدة الإيقاف (دقائق)")
            .setView(picker)
            .setPositiveButton("موافق") { _, _ ->
                viewModel.disableInternetFor(picker.value)
            }
            .setNegativeButton("إلغاء", null)
            .show()
        return 0
    }

    private fun showSpeedTestDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("اختبار السرعة")
            .setMessage("سيتم قياس سرعة الإنترنت الحالية")
            .setPositiveButton("ابدأ") { _, _ ->
                Snackbar.make(binding.root, "جاري اختبار السرعة...", Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showBlockAllConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("حجب جميع الأجهزة")
            .setMessage("هل تريد قطع الإنترنت عن جميع الأجهزة المتصلة؟")
            .setPositiveButton("نعم") { _, _ ->
                viewModel.toggleInternet()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
