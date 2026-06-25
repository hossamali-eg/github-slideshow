package com.wifimanager.ui.scheduler

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.wifimanager.data.models.ScheduleAction
import com.wifimanager.data.models.ScheduleRule
import com.wifimanager.data.models.ScheduleRuleType
import com.wifimanager.databinding.FragmentSchedulerBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SchedulerFragment : Fragment() {

    private var _binding: FragmentSchedulerBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SchedulerViewModel by viewModels()
    private lateinit var adapter: ScheduleAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSchedulerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ScheduleAdapter(
            onToggle = { rule, enabled -> viewModel.toggleRule(rule.id, enabled) },
            onDelete = { showDeleteConfirmation(it) }
        )

        binding.rvScheduleRules.adapter = adapter
        binding.rvScheduleRules.layoutManager = LinearLayoutManager(requireContext())

        viewModel.rules.observe(viewLifecycleOwner) { rules ->
            adapter.submitList(rules)
            binding.emptyState.visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.message.observe(viewLifecycleOwner) { msg ->
            if (!msg.isNullOrEmpty()) {
                Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
            }
        }

        binding.fabAddRule.setOnClickListener { showAddRuleDialog() }
    }

    private fun showAddRuleDialog() {
        var startTime = "22:00"
        var endTime = "06:00"

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 8)
        }

        val etName = EditText(requireContext()).apply {
            hint = "اسم القاعدة (مثل: إيقاف ليلاً)"
            setText("إيقاف الإنترنت")
        }

        val tvFrom = TextView(requireContext()).apply {
            text = "⏰ من: $startTime (اضغط للتغيير)"
            textSize = 14f
            setPadding(0, 16, 0, 8)
        }
        val tvTo = TextView(requireContext()).apply {
            text = "⏰ حتى: $endTime (اضغط للتغيير)"
            textSize = 14f
            setPadding(0, 8, 0, 8)
        }

        tvFrom.setOnClickListener {
            MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_12H)
                .setTitleText("وقت البدء")
                .build().apply {
                    addOnPositiveButtonClickListener {
                        startTime = "%02d:%02d".format(hour, minute)
                        tvFrom.text = "⏰ من: $startTime"
                    }
                    show(parentFragmentManager, "fromTime")
                }
        }

        tvTo.setOnClickListener {
            MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_12H)
                .setTitleText("وقت الانتهاء")
                .build().apply {
                    addOnPositiveButtonClickListener {
                        endTime = "%02d:%02d".format(hour, minute)
                        tvTo.text = "⏰ حتى: $endTime"
                    }
                    show(parentFragmentManager, "toTime")
                }
        }

        // Days checkboxes
        val dayNames = arrayOf("أحد", "اثنين", "ثلاثاء", "أربعاء", "خميس", "جمعة", "سبت")
        val dayChecks = dayNames.mapIndexed { i, name ->
            CheckBox(requireContext()).apply {
                text = name
                isChecked = i < 5  // default weekdays
                setTextColor(requireContext().getColor(android.R.color.white))
            }
        }
        val daysLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            dayChecks.forEach { addView(it) }
        }

        layout.addView(etName)
        layout.addView(tvFrom)
        layout.addView(tvTo)
        layout.addView(TextView(requireContext()).apply {
            text = "الأيام:"
            textSize = 14f
            setPadding(0, 12, 0, 4)
        })
        layout.addView(daysLayout)

        AlertDialog.Builder(requireContext())
            .setTitle("إضافة قاعدة جدولة")
            .setView(layout)
            .setPositiveButton("حفظ") { _, _ ->
                val days = dayChecks.mapIndexedNotNull { i, cb -> if (cb.isChecked) (i + 1).toString() else null }
                    .joinToString(",")
                viewModel.addRule(ScheduleRule(
                    name = etName.text.toString().ifEmpty { "قاعدة جديدة" },
                    ruleType = ScheduleRuleType.INTERNET_TOGGLE,
                    action = ScheduleAction.DISABLE_INTERNET,
                    startTime = startTime,
                    endTime = endTime,
                    daysOfWeek = days.ifEmpty { "1,2,3,4,5" }
                ))
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showDeleteConfirmation(rule: ScheduleRule) {
        AlertDialog.Builder(requireContext())
            .setTitle("حذف القاعدة")
            .setMessage("هل تريد حذف قاعدة \"${rule.name}\"؟")
            .setPositiveButton("حذف") { _, _ -> viewModel.deleteRule(rule) }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
