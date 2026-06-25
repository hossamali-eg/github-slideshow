package com.wifimanager.ui.login

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.wifimanager.data.models.RouterConfig
import com.wifimanager.databinding.ItemSavedRouterBinding

class SavedRouterAdapter(
    private val onConnect: (RouterConfig) -> Unit,
    private val onDelete: (RouterConfig) -> Unit
) : ListAdapter<RouterConfig, SavedRouterAdapter.ViewHolder>(DIFF_CALLBACK) {

    inner class ViewHolder(private val binding: ItemSavedRouterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(config: RouterConfig) {
            binding.tvRouterName.text = config.name
            binding.tvRouterIp.text = config.ipAddress
            binding.tvRouterType.text = config.routerType.name
            if (config.isActive) {
                binding.tvActive.visibility = android.view.View.VISIBLE
            } else {
                binding.tvActive.visibility = android.view.View.GONE
            }
            binding.btnConnect.setOnClickListener { onConnect(config) }
            binding.btnDelete.setOnClickListener { onDelete(config) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSavedRouterBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<RouterConfig>() {
            override fun areItemsTheSame(old: RouterConfig, new: RouterConfig) = old.id == new.id
            override fun areContentsTheSame(old: RouterConfig, new: RouterConfig) = old == new
        }
    }
}
