package com.lorenzomarci.sosring

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lorenzomarci.sosring.databinding.ItemVipNumberBinding
import java.util.Locale

class VipNumbersAdapter(
    private val onEdit: (Int, VipContact) -> Unit,
    private val onDelete: (Int) -> Unit,
    private val onTrackTap: (VipContact) -> Unit
) : ListAdapter<VipContact, VipNumbersAdapter.ViewHolder>(DiffCallback) {

    object DiffCallback : DiffUtil.ItemCallback<VipContact>() {
        override fun areItemsTheSame(old: VipContact, new: VipContact) =
            old.number == new.number
        override fun areContentsTheSame(old: VipContact, new: VipContact) =
            old == new
    }

    inner class ViewHolder(private val binding: ItemVipNumberBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(contact: VipContact, position: Int) {
            binding.tvAvatar.text = contactInitials(contact.name)
            binding.tvName.text = contact.name
            binding.tvNumber.text = contact.number

            val tint = binding.tvName.currentTextColor

            binding.btnGps.visibility = if (Push.canRequestLocation(binding.root.context, contact.number)) View.VISIBLE else View.GONE
            binding.btnGps.setColorFilter(tint)
            binding.btnGps.setOnClickListener { onTrackTap(contact) }

            binding.btnMore.setColorFilter(tint)
            binding.btnMore.setOnClickListener { anchor -> showMoreMenu(anchor, position, contact) }
        }

        private fun showMoreMenu(anchor: View, position: Int, contact: VipContact) {
            val menu = PopupMenu(anchor.context, anchor)
            menu.menuInflater.inflate(R.menu.vip_row_menu, menu.menu)
            menu.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_edit -> { onEdit(position, contact); true }
                    R.id.action_delete -> { onDelete(position); true }
                    else -> false
                }
            }
            menu.show()
        }

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemVipNumberBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    private fun contactInitials(name: String): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val initials = when {
            parts.size >= 2 -> "${parts.first().first()}${parts.last().first()}"
            parts.size == 1 -> parts.first().take(2)
            else -> "?"
        }
        return initials.uppercase(Locale.getDefault())
    }
}
