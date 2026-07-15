package com.wifimanager.ui.settings

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.wifimanager.databinding.FragmentSettingsBinding
import com.wifimanager.ui.login.LoginActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.cardChangeRouter.setOnClickListener {
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()
        }

        binding.cardRemoteAccess.setOnClickListener {
            showRemoteAccessSetup()
        }

        binding.cardAbout.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("WiFi Manager")
                .setMessage("الإصدار 1.0.0\nتطبيق للتحكم الكامل في الشبكة وإدارة الأجهزة المتصلة")
                .setPositiveButton("حسناً", null)
                .show()
        }

        binding.switchNotifications.setOnCheckedChangeListener { _, checked ->
            Snackbar.make(binding.root,
                if (checked) "تم تفعيل الإشعارات" else "تم إيقاف الإشعارات",
                Snackbar.LENGTH_SHORT).show()
        }

        binding.switchAutoRefresh.setOnCheckedChangeListener { _, checked ->
            Snackbar.make(binding.root,
                if (checked) "تم تفعيل التحديث التلقائي" else "تم إيقاف التحديث التلقائي",
                Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun showRemoteAccessSetup() {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 8)
        }

        layout.addView(TextView(requireContext()).apply {
            text = "للتحكم عن بُعد بدون الاتصال بنفس الواي فاي، يلزمك:"
            textSize = 13f
            setPadding(0, 0, 0, 12)
        })

        layout.addView(TextView(requireContext()).apply {
            text = "1. تفعيل Remote Management في الراوتر\n2. إعداد Dynamic DNS\n3. أو استخدام Firebase Remote Control"
            textSize = 12f
        })

        val etFirebaseUrl = EditText(requireContext()).apply {
            hint = "Firebase Database URL (اختياري)"
            setPadding(0, 16, 0, 0)
        }
        layout.addView(etFirebaseUrl)

        AlertDialog.Builder(requireContext())
            .setTitle("الوصول عن بُعد")
            .setView(layout)
            .setPositiveButton("حفظ") { _, _ ->
                if (etFirebaseUrl.text.isNotEmpty()) {
                    Snackbar.make(binding.root, "تم حفظ إعدادات الوصول عن بُعد", Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
