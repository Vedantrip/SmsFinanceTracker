package com.example.smsfinancetracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import android.widget.Toast
import com.example.smsfinancetracker.data.AppDatabase
import com.example.smsfinancetracker.data.TransactionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.regex.Pattern

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

            messages?.forEach { sms ->
                val body = sms.messageBody ?: ""
                val date = sms.timestampMillis

                // Check for ANY expense keyword (not just "Sent Rs.")
                if (body.contains("Sent Rs.", true) ||
                    body.contains("debited", true) ||
                    body.contains("spent", true) ||
                    body.contains("paid", true)) {

                    val amount = extractAmount(body)

                    if (amount > 0) {
                        val merchant = extractMerchant(body)
                        saveTransaction(context, amount, merchant, date, "DEBIT")
                    }
                }
            }
        }
    }

    // ================= SMART PARSING (SAME AS DASHBOARD) =================

    private fun extractAmount(body: String): Double {
        // Matches: "Rs. 500", "INR 500", "Rs 500.00", "Sent Rs.500"
        val pattern = Pattern.compile("(?:Rs\\.?|INR)\\s*(\\d+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(body)
        return if (matcher.find()) matcher.group(1)?.toDoubleOrNull() ?: 0.0 else 0.0
    }

    private fun extractMerchant(body: String): String {
        val cleanBody = body.replace(Regex("(?i)(Info:)|(Txn)|(Ref)|(No\\.)"), "")

        // 1. Keyword Search (Most reliable)
        if (cleanBody.contains("zomato", true)) return "Zomato"
        if (cleanBody.contains("swiggy", true)) return "Swiggy"
        if (cleanBody.contains("uber", true)) return "Uber"
        if (cleanBody.contains("ola", true)) return "Ola"
        if (cleanBody.contains("blinkit", true)) return "Blinkit"
        if (cleanBody.contains("amazon", true)) return "Amazon"
        if (cleanBody.contains("netflix", true)) return "Netflix"
        if (cleanBody.contains("spotify", true)) return "Spotify"
        if (cleanBody.contains("recharge", true) || cleanBody.contains("jio", true) || cleanBody.contains("airtel", true)) return "Mobile Recharge"

        // 2. Try to find "at [MERCHANT]" (e.g. "at STARBUCKS")
        var pattern = Pattern.compile("at\\s+([A-Za-z0-9\\s_\\-]+?)(?:\\s|\\.|,|$)", Pattern.CASE_INSENSITIVE)
        var matcher = pattern.matcher(cleanBody)
        if (matcher.find()) {
            return matcher.group(1)?.trim()?.take(20) ?: "Unknown"
        }

        // 3. Try to find "to [UPI ID / NAME]"
        pattern = Pattern.compile("to\\s+([A-Za-z0-9@._\\s\\-]+?)(?:\\s+on|\\s|\\.|,|$)", Pattern.CASE_INSENSITIVE)
        matcher = pattern.matcher(cleanBody)
        if (matcher.find()) {
            val raw = matcher.group(1)?.trim() ?: ""
            // Cleanup UPI IDs (e.g., "starbucks@hdfc" -> "starbucks")
            return if (raw.contains("@")) raw.split("@")[0] else raw.take(20)
        }

        return "Unknown"
    }

    // ================= SAVE TO DB =================

    private fun saveTransaction(context: Context, amount: Double, merchant: String, date: Long, type: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val transaction = TransactionEntity(
                    merchant = merchant,
                    amount = amount,
                    timestamp = date,
                    type = type
                )
                AppDatabase.get(context).transactionDao().insert(transaction)
                Log.d("SmsReceiver", "Saved Transaction: ₹$amount at $merchant")

                // Optional: Show a quick Toast so you know it worked
                showToast(context, "Tracked: ₹$amount at $merchant")
            } catch (e: Exception) {
                Log.e("SmsReceiver", "Error saving transaction: ${e.message}")
            }
        }
    }

    private fun showToast(context: Context, message: String) {
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
}