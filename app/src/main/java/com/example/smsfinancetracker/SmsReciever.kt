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
                val sender = sms.originatingAddress ?: ""

                // 1. Check for KOTAK (Expense/Sent)
                if (body.contains("Sent Rs.", ignoreCase = true)) {
                    handleKotakExpense(context, body)
                }

                // 2. Check for SBI (Income/Credit)
                else if (body.contains("credited by Rs.", ignoreCase = true)) {
                    handleSbiIncome(context, body)
                }
            }
        }
    }

    // ================= KOTAK LOGIC (EXPENSE) =================
    // Format: "Sent Rs.70.00 from Kotak Bank AC X9192 to 6396253142@pthdfc on..."
    private fun handleKotakExpense(context: Context, body: String) {
        val amount = extractAmount(body, "Sent Rs.")
        var merchant = "Unknown"

        // Extract merchant: Find text between "to " and " on"
        // Example: "... to 6396253142@pthdfc on ..."
        val merchantPattern = Pattern.compile("to\\s+(.*?)\\s+on")
        val matcher = merchantPattern.matcher(body)
        if (matcher.find()) {
            merchant = matcher.group(1) ?: "Unknown"
            // Clean up UPI IDs (optional)
            if (merchant.contains("@")) {
                merchant = merchant.split("@")[0] // Just take the name/number part
            }
        }

        saveTransaction(context, amount, merchant, "DEBIT")
        showToast(context, "Spent ₹$amount at $merchant")
    }

    // ================= SBI LOGIC (INCOME) =================
    // Format: "...credited by Rs.5000.00 on ... -ANSHUMAN TRIPATHI..."
    private fun handleSbiIncome(context: Context, body: String) {
        val amount = extractAmount(body, "credited by Rs.")

        // For income, "Merchant" is the Sender
        saveTransaction(context, amount, "Deposit / Transfer", "CREDIT")
        showToast(context, "Received ₹$amount")
    }

    // ================= HELPER FUNCTIONS =================

    private fun extractAmount(body: String, keyword: String): Double {
        // Look for the keyword followed by digits
        // Escape the dot in "Rs." -> "Rs\\."
        val escapedKeyword = keyword.replace(".", "\\.")
        val pattern = Pattern.compile("$escapedKeyword\\s*(\\d+(\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(body)

        return if (matcher.find()) {
            matcher.group(1)?.toDoubleOrNull() ?: 0.0
        } else {
            0.0
        }
    }

    private fun saveTransaction(context: Context, amount: Double, merchant: String, type: String) {
        if (amount == 0.0) return

        CoroutineScope(Dispatchers.IO).launch {
            val transaction = TransactionEntity(
                merchant = merchant,
                amount = amount,
                timestamp = System.currentTimeMillis(),
                type = type
            )
            AppDatabase.get(context).transactionDao().insert(transaction)
            Log.d("SmsReceiver", "Saved $type: $amount")
        }
    }

    private fun showToast(context: Context, message: String) {
        // Need to run Toast on Main Thread
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
}