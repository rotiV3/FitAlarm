package com.rotiv3.fitalarm.ui.achievement

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rotiv3.fitalarm.R
import com.rotiv3.fitalarm.databinding.FragmentAchievementBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AchievementFragment : Fragment() {

    private var _binding: FragmentAchievementBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AchievementViewModel by viewModels()
    private lateinit var achievementAdapter: AchievementAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAchievementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeAchievements()
    }

    private fun setupRecyclerView() {
        achievementAdapter = AchievementAdapter()
        binding.rvAchievements.adapter = achievementAdapter
        binding.rvAchievements.layoutManager = LinearLayoutManager(requireContext())
        attachSwipeToShare()
    }

    private fun attachSwipeToShare() {
        val shareIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_share)
        val background = ColorDrawable(Color.parseColor("#1565C0"))
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 36f
            textAlign = Paint.Align.RIGHT
        }

        val callback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                if (position == RecyclerView.NO_ID.toInt()) return
                val item = achievementAdapter.currentList.getOrNull(position) ?: return
                // Restore the card to its original position before sharing
                achievementAdapter.notifyItemChanged(position)
                shareAchievement(item)
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                val itemHeight = itemView.bottom - itemView.top

                // Only paint the revealed area (right side as card slides left)
                val revealLeft = (itemView.right + dX).toInt()
                if (revealLeft < itemView.right) {
                    // Draw blue background in revealed area with corner radius
                    background.setBounds(revealLeft, itemView.top, itemView.right, itemView.bottom)
                    background.draw(c)

                    // Draw share icon centred vertically in the revealed strip
                    shareIcon?.let { icon ->
                        val iconSize = (itemHeight * 0.35f).toInt().coerceAtMost(icon.intrinsicHeight)
                        val iconMarginH = 28
                        val iconTop = itemView.top + (itemHeight - iconSize) / 2
                        val iconRight = itemView.right - iconMarginH
                        val iconLeft = iconRight - iconSize
                        if (iconLeft >= revealLeft) {
                            icon.setBounds(iconLeft, iconTop, iconRight, iconTop + iconSize)
                            icon.draw(c)
                        }
                    }

                    // "Share" label to the left of the icon
                    val labelX = itemView.right - 28f - (shareIcon?.intrinsicWidth ?: 0) - 8f
                    val labelY = itemView.top + itemHeight / 2f + labelPaint.textSize / 3
                    if (labelX - labelPaint.measureText("Share") >= revealLeft) {
                        c.drawText("Share", labelX, labelY, labelPaint)
                    }
                }

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }

            override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder) = 0.35f
        }

        ItemTouchHelper(callback).attachToRecyclerView(binding.rvAchievements)
    }

    private fun observeAchievements() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.achievements.collect { achievements ->
                    if (achievements.isEmpty()) {
                        binding.tvEmpty.visibility = View.VISIBLE
                        binding.rvAchievements.visibility = View.GONE
                    } else {
                        binding.tvEmpty.visibility = View.GONE
                        binding.rvAchievements.visibility = View.VISIBLE
                        achievementAdapter.submitList(achievements)
                    }
                }
            }
        }
    }

    private fun shareAchievement(achievement: AchievementDisplay) {
        achievement.gymAchievementId?.let { viewModel.markShared(it) }

        val shareText = buildString {
            append("${achievement.emoji} I just earned the '${achievement.title}' badge in FitAlarm!\n")
            append(achievement.description)
            if (achievement.streakCount > 1) append("\nThat's a ${achievement.streakCount}-day streak!")
            append("\n#FitAlarm #Fitness #WorkoutMotivation")
        }

        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                    putExtra(Intent.EXTRA_SUBJECT, "FitAlarm Achievement")
                },
                "Share Achievement"
            )
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
