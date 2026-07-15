package com.wifimanager.ui.scheduler

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.wifimanager.data.models.ScheduleRule
import com.wifimanager.data.models.ScheduleAction
import com.wifimanager.databinding.ItemScheduleRuleBinding

class ScheduleAdapter(
    private val onToggle: (ScheduleRule, Boolean) -> Unit,
    private val onDelete: (ScheduleRule) -> Unit
) : ListAdapter<ScheduleRule, ScheduleAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(private val b: ItemScheduleRuleBinding) :
        RecyclerView.ViewHolder(b.root) {
        fun bind(rule: ScheduleRule) {
            b.tvRuleName.text = rule.name
            b.tvRuleTime.text = if (rule.endTime.isNotEmpty())
                "${rule.startTime} - ${rule.endTime}"
            else rule.startTime
            b.tvRuleDays.text = formatDays(rule.daysOfWeek)
            b.tvRuleIcon.text = when (rule.action) {
                ScheduleAction.DISABLE_INTERNET, ScheduleAction.BLOCK -> "🚫"
                ScheduleAction.ENABLE_INTERNET, ScheduleAction.UNBLOCK -> "✅"
                ScheduleAction.LIMIT_SPEED -> "⚡"
                else -> "⏰"
            }
            b.switchRule.isChecked = rule.isEnabled
            b.switchRule.setOnCheckedChangeListener { _, checked -> onToggle(rule, checked) }
            b.root.setOnLongClickListener { onDelete(rule); true }
        }

        private fun formatDays(days: String): String {
            val names = mapOf(
                "1" to "أحد", "2" to "اثنين", "3" to "ثلاثاء",
                "4" to "أربعاء", "5" to "خميس", "6" to "جمعة", "7" to "سبت"
            )
            return days.split(",").mapNotNull { names[it.trim()] }.joinToString(" - ")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemScheduleRuleBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<ScheduleRule>() {
            override fun areItemsTheSame(old: ScheduleRule, new: ScheduleRule) = old.id == new.id
            override fun areContentsTheSame(old: ScheduleRule, new: ScheduleRule) = old == new
        }
    }
}
