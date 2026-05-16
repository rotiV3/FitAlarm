package com.rotiv3.fitalarm.ui.gym

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.rotiv3.fitalarm.data.model.GymLocation
import com.rotiv3.fitalarm.databinding.ItemGymLocationBinding

class GymLocationAdapter(
    private val onDelete: (GymLocation) -> Unit,
    private val onToggle: (GymLocation) -> Unit,
    private val onRename: (GymLocation, String) -> Unit = { _, _ -> }
) : ListAdapter<GymLocation, GymLocationAdapter.GymViewHolder>(GymDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GymViewHolder {
        val binding = ItemGymLocationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return GymViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GymViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class GymViewHolder(
        private val binding: ItemGymLocationBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(gym: GymLocation) {
            binding.tvGymName.setText(gym.name)
            binding.tvGymAddress.text = gym.address
            binding.tvGymRadius.text = "Radius: ${gym.radiusMeters.toInt()}m"
            binding.switchActive.isChecked = gym.isActive

            // Grey out the whole card when inactive
            binding.root.alpha = if (gym.isActive) 1f else 0.4f

            // Inline rename on done/focus-lost
            binding.tvGymName.setOnEditorActionListener { v, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    val newName = v.text.toString().trim().ifEmpty { gym.name }
                    if (newName != gym.name) onRename(gym, newName)
                    v.clearFocus()
                    true
                } else false
            }
            binding.tvGymName.setOnFocusChangeListener { v, hasFocus ->
                if (!hasFocus) {
                    val newName = (v as android.widget.EditText).text.toString().trim().ifEmpty { gym.name }
                    if (newName != gym.name) onRename(gym, newName)
                }
            }

            binding.switchActive.setOnCheckedChangeListener(null)
            binding.switchActive.setOnCheckedChangeListener { _, _ ->
                onToggle(gym)
            }
            binding.btnDeleteGym.setOnClickListener { onDelete(gym) }
        }
    }

    class GymDiffCallback : DiffUtil.ItemCallback<GymLocation>() {
        override fun areItemsTheSame(oldItem: GymLocation, newItem: GymLocation) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: GymLocation, newItem: GymLocation) =
            oldItem == newItem
    }
}
