package com.rotiv3.fitalarm.ui.calendar

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.rotiv3.fitalarm.data.model.ActivityType
import com.rotiv3.fitalarm.data.model.CalendarEvent
import com.rotiv3.fitalarm.data.model.SessionStatus
import com.rotiv3.fitalarm.databinding.ItemCalendarEventBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CalendarEventAdapter(
    private val onEventClick: (CalendarEvent) -> Unit
) : ListAdapter<CalendarEvent, CalendarEventAdapter.EventViewHolder>(EventDiffCallback()) {

    private var achievedEventIds: Set<String> = emptySet()

    fun setAchievedEventIds(ids: Set<String>) {
        achievedEventIds = ids
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val binding = ItemCalendarEventBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return EventViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class EventViewHolder(
        private val binding: ItemCalendarEventBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        fun bind(event: CalendarEvent) {
            binding.tvEventTitle.text = event.title

            val startStr = timeFormat.format(Date(event.startTime))
            val endStr = timeFormat.format(Date(event.endTime))
            binding.tvEventTime.text = "$startStr – $endStr"

            // Gym event colour-coding by session status
            if (event.isGymEvent) {
                binding.viewGymIndicator.visibility = View.VISIBLE
                val (indicatorColor, badgeText) = when (event.sessionStatus) {
                    SessionStatus.COMPLETED -> Color.parseColor("#4CAF50") to "✅ DONE"
                    SessionStatus.AT_GYM    -> Color.parseColor("#FFC107") to "📍 AT GYM"
                    SessionStatus.MISSED    -> Color.parseColor("#F44336") to "❌ MISSED"
                    SessionStatus.UPCOMING  -> if (event.activityType == ActivityType.OUTDOOR)
                        Color.parseColor("#2196F3") to "🌿 OUTDOOR"
                    else
                        Color.parseColor("#2196F3") to "💪 GYM"
                }
                binding.viewGymIndicator.setBackgroundColor(indicatorColor)
                binding.tvGymBadge.visibility = View.VISIBLE
                binding.tvGymBadge.text = badgeText
                binding.tvGymBadge.setTextColor(indicatorColor)
            } else {
                binding.viewGymIndicator.visibility = View.INVISIBLE
                binding.tvGymBadge.visibility = View.GONE
            }

            // Achievement trophy badge — shown when this outdoor event has a recorded session
            binding.tvAchievementBadge.visibility =
                if (event.activityType == ActivityType.OUTDOOR && event.id in achievedEventIds)
                    View.VISIBLE else View.GONE

            // Location
            if (!event.location.isNullOrBlank()) {
                binding.tvLocation.visibility = View.VISIBLE
                binding.tvLocation.text = event.location
                binding.ivLocationIcon.visibility = View.VISIBLE
            } else {
                binding.tvLocation.visibility = View.GONE
                binding.ivLocationIcon.visibility = View.GONE
            }

            binding.root.setOnClickListener { onEventClick(event) }
        }
    }

    class EventDiffCallback : DiffUtil.ItemCallback<CalendarEvent>() {
        override fun areItemsTheSame(oldItem: CalendarEvent, newItem: CalendarEvent) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: CalendarEvent, newItem: CalendarEvent) =
            oldItem == newItem
    }
}
