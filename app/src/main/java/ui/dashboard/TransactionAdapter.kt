package com.example.smsfinancetracker.ui.dashboard

import android.graphics.Color
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.smsfinancetracker.R
import com.example.smsfinancetracker.data.TransactionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransactionAdapter(private val transactions: List<TransactionEntity>) :
    RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    class TransactionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMerchant: TextView = view.findViewById(R.id.tvMerchant)
        val tvAmount: TextView = view.findViewById(R.id.tvAmount)
        val tvDate: TextView = view.findViewById(R.id.tvDate)
        val ivIcon: ImageView = view.findViewById(R.id.ivIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val transaction = transactions[position]

        // 1. Set Merchant Name
        holder.tvMerchant.text = transaction.merchant

        // 2. Set Amount (Red color)
        holder.tvAmount.text = "₹${"%.2f".format(transaction.amount)}"
        holder.tvAmount.setTextColor(Color.parseColor("#D32F2F")) // Red

        // 3. Set Smart Date ("Today", "Yesterday", etc.)
        holder.tvDate.text = getSmartDate(transaction.timestamp)

        // 4. Set Icon & Color based on Category
        val (iconRes, colorHex) = getCategoryStyle(transaction.merchant)

        holder.ivIcon.setImageResource(iconRes)
        holder.ivIcon.setColorFilter(Color.parseColor(colorHex))
    }

    override fun getItemCount() = transactions.size

    // ================= LOGIC TO PICK ICONS =================

    private fun getCategoryStyle(merchant: String): Pair<Int, String> {
        val name = merchant.lowercase()
        return when {
            // FOOD (Coral Color)
            name.contains("zomato") || name.contains("swiggy") ||
                    name.contains("chai") || name.contains("tea") ||
                    name.contains("coffee") || name.contains("pizza") ||
                    name.contains("burger") || name.contains("restaurant") ||
                    name.contains("food") ->
                Pair(R.drawable.ic_food, "#FF6F61")

            // TRAVEL (Blue Color)
            name.contains("uber") || name.contains("ola") ||
                    name.contains("rapido") || name.contains("fuel") ||
                    name.contains("petrol") || name.contains("metro") ||
                    name.contains("auto") ->
                Pair(R.drawable.ic_travel, "#4A90E2")

            // BILLS & UTILITIES (Orange Color)
            name.contains("recharge") || name.contains("jio") ||
                    name.contains("airtel") || name.contains("vi") ||
                    name.contains("bill") || name.contains("electricity") ||
                    name.contains("paytm") || name.contains("upi") ->  // Added Paytm here!
                Pair(R.drawable.ic_bill, "#FFB347")

            // SHOPPING (Purple Color)
            name.contains("amazon") || name.contains("flipkart") ||
                    name.contains("myntra") || name.contains("store") ||
                    name.contains("market") || name.contains("shop") ->
                Pair(R.drawable.ic_shopping, "#6B5B95")

            // ENTERTAINMENT (Pink Color)
            name.contains("netflix") || name.contains("spotify") ||
                    name.contains("movie") || name.contains("cinema") ->
                Pair(R.drawable.ic_entertainment, "#E91E63")

            // DEFAULT (Grey Dollar)
            else -> Pair(R.drawable.ic_money, "#757575")
        }
    }

    private fun getSmartDate(timestamp: Long): String {
        return if (DateUtils.isToday(timestamp)) {
            "Today"
        } else if (DateUtils.isToday(timestamp + DateUtils.DAY_IN_MILLIS)) {
            "Yesterday"
        } else {
            val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}