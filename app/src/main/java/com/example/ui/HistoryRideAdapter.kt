package com.example.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import com.example.data.local.RotaProRide
import com.example.databinding.ItemHistoryRideBinding
import com.example.model.RideVerdict
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Adapter de alta performance para exibir as corridas salvas no Room.
 */
class HistoryRideAdapter(
    private val onDeleteClick: (RotaProRide) -> Unit
) : ListAdapter<RotaProRide, HistoryRideAdapter.RideViewHolder>(DiffCallback) {

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    private val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR"))

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RideViewHolder {
        val binding = ItemHistoryRideBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return RideViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RideViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class RideViewHolder(
        private val binding: ItemHistoryRideBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: RotaProRide) {
            val context = binding.root.context
            val formattedTime = dateFormat.format(Date(item.timestamp))
            binding.tvItemAppAndTime.text = "${item.appName} • $formattedTime"
            binding.tvItemFare.text = currencyFormat.format(item.fare)
            binding.tvItemProfit.text = context.getString(
                R.string.history_item_profit_format,
                currencyFormat.format(item.profit)
            )
            binding.tvItemMetrics.text = context.getString(
                R.string.history_item_metrics_format,
                item.totalKm,
                item.totalMin,
                currencyFormat.format(item.grossPerKm),
                currencyFormat.format(item.grossPerHour)
            )

            val verdict = runCatching { RideVerdict.valueOf(item.verdict) }
                .getOrDefault(RideVerdict.REJECT)

            val (bgRes, accentRes, textRes, labelRes) = when (verdict) {
                RideVerdict.ACCEPT -> QuadStyle(
                    R.color.verdict_accept_bg,
                    R.color.verdict_accept_accent,
                    R.color.verdict_accept_text,
                    R.string.overlay_verdict_accept
                )
                RideVerdict.CAUTION -> QuadStyle(
                    R.color.verdict_caution_bg,
                    R.color.verdict_caution_accent,
                    R.color.verdict_caution_text,
                    R.string.overlay_verdict_caution
                )
                RideVerdict.REJECT -> QuadStyle(
                    R.color.verdict_reject_bg,
                    R.color.verdict_reject_accent,
                    R.color.verdict_reject_text,
                    R.string.overlay_verdict_reject
                )
            }

            val bgColor = ContextCompat.getColor(context, bgRes)
            val accentColor = ContextCompat.getColor(context, accentRes)
            val textColor = ContextCompat.getColor(context, textRes)

            binding.viewVerdictBar.setBackgroundColor(accentColor)
            binding.tvItemVerdictBadge.setBackgroundColor(bgColor)
            binding.tvItemVerdictBadge.setTextColor(textColor)
            binding.tvItemVerdictBadge.setText(labelRes)
            binding.tvItemProfit.setTextColor(accentColor)

            binding.btnDeleteRide.setOnClickListener {
                onDeleteClick(item)
            }
        }
    }

    private data class QuadStyle(
        val bgRes: Int,
        val accentRes: Int,
        val textRes: Int,
        val labelRes: Int
    )

    private object DiffCallback : DiffUtil.ItemCallback<RotaProRide>() {
        override fun areItemsTheSame(oldItem: RotaProRide, newItem: RotaProRide): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: RotaProRide, newItem: RotaProRide): Boolean {
            return oldItem == newItem
        }
    }
}
