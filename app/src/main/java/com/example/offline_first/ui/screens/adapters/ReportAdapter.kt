package com.example.offline_first.ui.screens.adapters

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.offline_first.R
import com.example.offline_first.data.model.Report
import com.example.offline_first.data.model.SyncStatus
import com.example.offline_first.databinding.ItemReportBinding

class ReportAdapter : ListAdapter<Report, ReportAdapter.ViewHolder>(DiffCallback) {

    //   The type: ((Report) -> Unit)?
    //
    //  - (Report) -> Unit — a function that takes one Report argument and returns nothing (Unit = void)
    //  - The outer ? makes it nullable — it can be null if no one has assigned a function to it
    //
    //  = null — starts as null, meaning "no handler registered yet"
    var onItemClick: ((Report) -> Unit)? = null
    var onDeleteClick: ((Report) -> Unit)? = null
    var onRestoreClick: ((Report) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemReportBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onItemClick, onDeleteClick, onRestoreClick)
    }

    class ViewHolder(private val binding: ItemReportBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            report: Report,
            onItemClick: ((Report) -> Unit)?,
            onDeleteClick: ((Report) -> Unit)?,
            onRestoreClick: ((Report) -> Unit)?
        ) {
            val context = binding.root.context
            binding.root.setOnClickListener { onItemClick?.invoke(report) }

            // action button: restore if pending-delete, trash otherwise
            if (report.syncStatus == SyncStatus.DELETED_PENDING) {
                binding.buttonRemove.setIconResource(R.drawable.ic_restore)
                binding.buttonRemove.iconTint = ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.sync_pending)
                )
                binding.buttonRemove.contentDescription = "Restore report"
                binding.buttonRemove.setOnClickListener { onRestoreClick?.invoke(report) }
            } else {
                binding.buttonRemove.setIconResource(R.drawable.ic_delete)
                binding.buttonRemove.iconTint = ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.sync_deleting)
                )
                binding.buttonRemove.contentDescription = "Remove report"
                binding.buttonRemove.setOnClickListener { onDeleteClick?.invoke(report) }
            }

            binding.textTitle.text = report.title
            val attachmentIcon = if (report.photoPath != null) {
                ContextCompat.getDrawable(context, R.drawable.ic_attachment)?.apply {
                    setBounds(0, 0, intrinsicWidth, intrinsicHeight)
                }
            } else null
            binding.textTitle.setCompoundDrawablesRelative(null, null, attachmentIcon, null)
            binding.textTitle.compoundDrawablePadding = 8

            binding.textDescription.text = report.description
            binding.textCategory.text = report.category
            binding.textLocation.text = "📍 ${report.location}"

            val (label, @ColorRes colorRes, @DrawableRes iconRes) = when (report.syncStatus) {
                SyncStatus.SYNCED          -> Triple("Synced",          R.color.sync_synced,    R.drawable.ic_synced)
                SyncStatus.PENDING         -> Triple("Pending",         R.color.sync_pending,   R.drawable.ic_pending)
                SyncStatus.FAILED          -> Triple("Failed",          R.color.sync_deleting,  R.drawable.ic_deleting)
                SyncStatus.DELETED_PENDING -> Triple("Delete Pending",  R.color.sync_deleting,  R.drawable.ic_deleting)
            }

            val color = ContextCompat.getColor(context, colorRes)
            val icon = ContextCompat.getDrawable(context, iconRes)!!.mutate()
            DrawableCompat.setTint(icon, color)

            binding.textSyncStatus.text = label
            binding.textSyncStatus.setTextColor(color)
            binding.textSyncStatus.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
            binding.textSyncStatus.compoundDrawablePadding = 8
        }
    }

    // this is the smart part. When you call submitList(newList), ListAdapter runs DiffCallback on a background thread to figure out exactly what changed (added,
    //  removed, moved, updated). It then animates only those changes instead of redrawing the whole list.
    private object DiffCallback : DiffUtil.ItemCallback<Report>() {
        override fun areItemsTheSame(old: Report, new: Report) = old.id == new.id
        override fun areContentsTheSame(old: Report, new: Report) = old == new
    }
}