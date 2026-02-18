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

                // 1. Check for CREDIT (Income)
                if (body.contains("credited", true) ||
                    body.contains("received", true) ||
                    body.contains("deposited", true)) {

                    val amount = extractAmount(body)
                    if (amount > 0) {
                        saveTransaction(context, amount, "Income", date, "CREDIT")
                    }
                }
                // 2. Check for DEBIT (Expense)
                else if (body.contains("debited", true) ||
                    body.contains("sent", true) ||
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

    // ================= PARSING LOGIC =================

    private fun extractAmount(body: String): Double {
        val pattern = Pattern.compile("(?:Rs\\.?|INR)\\s*(\\d+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(body)
        return if (matcher.find()) matcher.group(1)?.toDoubleOrNull() ?: 0.0 else 0.0
    }

    private fun extractMerchant(body: String): String {
        val cleanBody = body.replace(Regex("(?i)(Info:)|(Txn)|(Ref)|(No\\.)"), "")

        // Common Merchants
        if (cleanBody.contains("zomato", true)) return "Zomato"
        if (cleanBody.contains("swiggy", true)) return "Swiggy"
        if (cleanBody.contains("uber", true)) return "Uber"
        if (cleanBody.contains("ola", true)) return "Ola"
        if (cleanBody.contains("blinkit", true)) return "Blinkit"
        if (cleanBody.contains("amazon", true)) return "Amazon"
        if (cleanBody.contains("netflix", true)) return "Netflix"
        if (cleanBody.contains("spotify", true)) return "Spotify"
        if (cleanBody.contains("recharge", true) || cleanBody.contains("jio", true) || cleanBody.contains("airtel", true)) return "Mobile Recharge"

        // "At" or "To" logic
        var pattern = Pattern.compile("at\\s+([A-Za-z0-9\\s_\\-]+?)(?:\\s|\\.|,|$)", Pattern.CASE_INSENSITIVE)
        var matcher = pattern.matcher(cleanBody)
        if (matcher.find()) return matcher.group(1)?.trim()?.take(20) ?: "Unknown"

        pattern = Pattern.compile("to\\s+([A-Za-z0-9@._\\s\\-]+?)(?:\\s+on|\\s|\\.|,|$)", Pattern.CASE_INSENSITIVE)
        matcher = pattern.matcher(cleanBody)
        if (matcher.find()) {
            val raw = matcher.group(1)?.trim() ?: ""
            return if (raw.contains("@")) raw.split("@")[0] else raw.take(20)
        }
        return "Unknown"
    }

    private fun saveTransaction(context: Context, amount: Double, merchant: String, date: Long, type: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val transaction = TransactionEntity(
                merchant = merchant,
                amount = amount,
                timestamp = date,
                type = type
            )
            AppDatabase.get(context).transactionDao().insert(transaction)

            CoroutineScope(Dispatchers.Main).launch {
                val symbol = if (type == "CREDIT") "+" else "-"
                Toast.makeText(context, "$type: $symbol₹$amount", Toast.LENGTH_SHORT).show()
            }
        }
    }
}