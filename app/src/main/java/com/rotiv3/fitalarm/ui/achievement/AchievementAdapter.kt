package com.rotiv3.fitalarm.ui.achievement

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.rotiv3.fitalarm.databinding.ItemAchievementBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AchievementAdapter : ListAdapter<AchievementDisplay, AchievementAdapter.AchievementViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AchievementViewHolder {
        val binding = ItemAchievementBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return AchievementViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AchievementViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AchievementViewHolder(
        private val binding: ItemAchievementBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

        fun bind(item: AchievementDisplay) {
            binding.tvTrophyEmoji.text = item.emoji
            binding.tvAchievementTitle.text = item.title
            binding.tvAchievementDescription.text = item.description
            binding.tvGymEvent.text = item.contextText
            binding.tvAchievementDate.text = dateFormat.format(Date(item.earnedAt))

            if (item.streakCount > 1) {
                binding.tvStreakBadge.visibility = android.view.View.VISIBLE
                binding.tvStreakBadge.text = "${item.streakCount} day streak"
            } else {
                binding.tvStreakBadge.visibility = android.view.View.GONE
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<AchievementDisplay>() {
        override fun areItemsTheSame(oldItem: AchievementDisplay, newItem: AchievementDisplay) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: AchievementDisplay, newItem: AchievementDisplay) =
            oldItem == newItem
    }
}
