package com.example.smsfinancetracker.ui.dashboard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smsfinancetracker.R
import com.example.smsfinancetracker.data.AppDatabase
import com.example.smsfinancetracker.data.TransactionEntity
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

class DashboardFragment : Fragment() {

    // --- UI Views ---
    private lateinit var tvTotal: TextView
    private lateinit var tvSmartInsight: TextView
    private lateinit var pieChart: PieChart
    private lateinit var rvTransactions: RecyclerView
    private lateinit var fabAdd: ExtendedFloatingActionButton
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var tvBudgetLeft: TextView
    private lateinit var layoutEmptyState: View // The new "No Data" view

    // --- Config ---
    private var monthlyBudget = 5000.0
    private var currentTransactions: MutableList<TransactionEntity> = mutableListOf() // Store list locally for swipe access

    // Custom Colors
    private val CHART_COLORS = listOf(
        Color.parseColor("#FF6F61"), Color.parseColor("#6B5B95"),
        Color.parseColor("#88B04B"), Color.parseColor("#F7CAC9"),
        Color.parseColor("#92A8D1"), Color.parseColor("#FFB347")
    )

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean -> if (isGranted) syncSmsAndLoad() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_dashboard, container, false)

        // Initialize Views
        tvTotal = view.findViewById(R.id.tvTotal)
        tvSmartInsight = view.findViewById(R.id.tvSmartInsight)
        pieChart = view.findViewById(R.id.pieChart)
        rvTransactions = view.findViewById(R.id.rvTransactions)
        fabAdd = view.findViewById(R.id.fabAdd)
        progressBar = view.findViewById(R.id.progressBar)
        tvBudgetLeft = view.findViewById(R.id.tvBudgetLeft)
        layoutEmptyState = view.findViewById(R.id.layoutEmptyState)

        rvTransactions.layoutManager = LinearLayoutManager(requireContext())
        rvTransactions.adapter = TransactionAdapter(emptyList())

        // Setup Swipe-to-Delete
        setupSwipeToDelete()

        loadBudgetFromPrefs()

        fabAdd.setOnClickListener {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) // Haptic!
            showAddTransactionDialog()
        }

        tvBudgetLeft.setOnClickListener {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) // Haptic!
            showSetBudgetDialog()
        }

        setupPieChartStyle()
        checkPermissionAndLoad()

        return view
    }

    // ================= SWIPE TO DELETE LOGIC =================

    private fun setupSwipeToDelete() {
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

            // Draw the Red Background when swiping
            override fun onChildDraw(
                c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder,
                dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                val background = ColorDrawable(Color.parseColor("#FF5252")) // Red Color
                val icon = ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_menu_delete)

                // Draw Red Background
                background.setBounds(
                    itemView.right + dX.toInt(), itemView.top,
                    itemView.right, itemView.bottom
                )
                background.draw(c)

                // Draw Icon centered
                icon?.let {
                    val iconMargin = (itemView.height - it.intrinsicHeight) / 2
                    val iconTop = itemView.top + (itemView.height - it.intrinsicHeight) / 2
                    val iconBottom = iconTop + it.intrinsicHeight
                    val iconLeft = itemView.right - iconMargin - it.intrinsicWidth
                    val iconRight = itemView.right - iconMargin

                    it.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                    it.draw(c)
                }

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }

            override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val transactionToDelete = currentTransactions[position]

                // Haptic Feedback
                view?.performHapticFeedback(HapticFeedbackConstants.CONFIRM)

                deleteTransaction(transactionToDelete)
            }
        }

        val itemTouchHelper = ItemTouchHelper(swipeHandler)
        itemTouchHelper.attachToRecyclerView(rvTransactions)
    }

    private fun deleteTransaction(transaction: TransactionEntity) {
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = AppDatabase.get(requireContext()).transactionDao()
            dao.delete(transaction)

            // Refresh UI
            loadDashboardData()

            // Show Undo Snackbar
            withContext(Dispatchers.Main) {
                Snackbar.make(rvTransactions, "Transaction deleted", Snackbar.LENGTH_LONG)
                    .setAction("UNDO") {
                        undoDelete(transaction)
                    }
                    .setAnchorView(fabAdd) // Show above FAB
                    .show()
            }
        }
    }

    private fun undoDelete(transaction: TransactionEntity) {
        lifecycleScope.launch(Dispatchers.IO) {
            AppDatabase.get(requireContext()).transactionDao().insert(transaction)
            loadDashboardData()
        }
    }

    // ================= BUDGET STORAGE =================

    private fun loadBudgetFromPrefs() {
        val prefs = requireContext().getSharedPreferences("FinancePrefs", Context.MODE_PRIVATE)
        monthlyBudget = prefs.getFloat("USER_BUDGET", 5000f).toDouble()
    }

    private fun saveBudgetToPrefs(newBudget: Double) {
        val prefs = requireContext().getSharedPreferences("FinancePrefs", Context.MODE_PRIVATE)
        prefs.edit().putFloat("USER_BUDGET", newBudget.toFloat()).apply()
        monthlyBudget = newBudget
        lifecycleScope.launch { loadDashboardData() }
    }

    // ================= DASHBOARD DATA =================

    private suspend fun loadDashboardData() {
        val dao = AppDatabase.get(requireContext()).transactionDao()
        val all = dao.getAll()
        val expenseTransactions = all.filter { it.type == "DEBIT" }

        var total = 0.0
        val categoryMap = mutableMapOf<String, Double>()

        expenseTransactions.forEach {
            total += it.amount
            val cat = getCategory(it.merchant)
            categoryMap[cat] = categoryMap.getOrDefault(cat, 0.0) + it.amount
        }

        // Store globally for Swipe Handler
        currentTransactions = expenseTransactions.sortedByDescending { it.timestamp }.toMutableList()
        val recentList = currentTransactions.take(10)

        withContext(Dispatchers.Main) {
            tvTotal.text = "₹${"%.2f".format(total)}"

            // Toggle Empty State
            if (recentList.isEmpty()) {
                rvTransactions.visibility = View.GONE
                layoutEmptyState.visibility = View.VISIBLE
            } else {
                rvTransactions.visibility = View.VISIBLE
                layoutEmptyState.visibility = View.GONE
            }

            // Budget Bar
            val budget = if (monthlyBudget == 0.0) 1.0 else monthlyBudget
            val percentage = (total / budget) * 100
            progressBar.progress = percentage.toInt()

            val remaining = budget - total
            if (remaining > 0) {
                tvBudgetLeft.text = "₹${"%.0f".format(remaining)} left of ₹${"%.0f".format(budget)} budget\n(Tap to Edit)"
            } else {
                tvBudgetLeft.text = "Budget exceeded by ₹${"%.0f".format(Math.abs(remaining))}\n(Tap to Edit)"
            }

            // Smart Colors
            if (percentage < 70) {
                progressBar.setIndicatorColor(Color.WHITE)
                tvSmartInsight.text = "You are saving well! 👏"
            } else if (percentage < 90) {
                progressBar.setIndicatorColor(Color.parseColor("#FFEB3B"))
                tvSmartInsight.text = "Careful, you're nearing your limit."
            } else {
                progressBar.setIndicatorColor(Color.parseColor("#FF5252"))
                tvSmartInsight.text = "You overspent this month! 🚨"
            }

            updatePieChart(categoryMap)
            rvTransactions.adapter = TransactionAdapter(recentList)
        }
    }

    // ================= POPUPS =================

    private fun showAddTransactionDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_add_transaction, null)
        dialog.setContentView(sheetView)

        val etAmount = sheetView.findViewById<TextInputEditText>(R.id.etAmount)
        val etDescription = sheetView.findViewById<TextInputEditText>(R.id.etDescription)
        val btnSave = sheetView.findViewById<Button>(R.id.btnSave)

        btnSave.setOnClickListener {
            val amountStr = etAmount.text.toString()
            val desc = etDescription.text.toString()
            if (amountStr.isNotEmpty() && desc.isNotEmpty()) {
                val amount = amountStr.toDoubleOrNull() ?: 0.0
                saveManualTransaction(amount, desc)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showSetBudgetDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_set_budget, null)
        dialog.setContentView(sheetView)

        val etBudget = sheetView.findViewById<TextInputEditText>(R.id.etBudget)
        val btnSave = sheetView.findViewById<Button>(R.id.btnSave)

        etBudget.setText(monthlyBudget.toInt().toString())

        btnSave.setOnClickListener {
            val budgetStr = etBudget.text.toString()
            if (budgetStr.isNotEmpty()) {
                val newBudget = budgetStr.toDoubleOrNull() ?: 5000.0
                saveBudgetToPrefs(newBudget)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    // ================= HELPERS =================

    private fun saveManualTransaction(amount: Double, description: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val transaction = TransactionEntity(
                merchant = description, amount = amount, timestamp = System.currentTimeMillis(), type = "DEBIT"
            )
            AppDatabase.get(requireContext()).transactionDao().insert(transaction)
            loadDashboardData()
        }
    }

    private fun checkPermissionAndLoad() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            syncSmsAndLoad()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.READ_SMS)
        }
    }

    private fun syncSmsAndLoad() {
        lifecycleScope.launch(Dispatchers.IO) {
            readInboxAndSaveToDb()
            loadDashboardData()
        }
    }

    private suspend fun readInboxAndSaveToDb() {
        val dao = AppDatabase.get(requireContext()).transactionDao()
        if (dao.getAll().isNotEmpty()) return

        try {
            val cursor = requireContext().contentResolver.query(
                Uri.parse("content://sms/inbox"), null, null, null, null
            )
            cursor?.use {
                val bodyIndex = it.getColumnIndex("body")
                val dateIndex = it.getColumnIndex("date")
                while (it.moveToNext()) {
                    val body = it.getString(bodyIndex) ?: ""
                    val date = it.getLong(dateIndex)
                    if (body.contains("Sent Rs.", true) || body.contains("debited", true) || body.contains("spent", true) || body.contains("paid", true)) {
                        val amount = extractAmount(body)
                        if (amount > 0) {
                            val merchant = extractMerchant(body)
                            val transaction = TransactionEntity(merchant = merchant, amount = amount, timestamp = date, type = "DEBIT")
                            dao.insert(transaction)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Dashboard", "Error reading SMS: ${e.message}")
        }
    }

    private fun extractAmount(body: String): Double {
        val pattern = Pattern.compile("(?:Rs\\.?|INR)\\s*(\\d+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(body)
        return if (matcher.find()) matcher.group(1)?.toDoubleOrNull() ?: 0.0 else 0.0
    }

    private fun extractMerchant(body: String): String {
        val cleanBody = body.replace(Regex("(?i)(Info:)|(Txn)|(Ref)|(No\\.)"), "")
        if (cleanBody.contains("zomato", true)) return "Zomato"
        if (cleanBody.contains("swiggy", true)) return "Swiggy"
        if (cleanBody.contains("uber", true)) return "Uber"
        if (cleanBody.contains("ola", true)) return "Ola"
        if (cleanBody.contains("blinkit", true)) return "Blinkit"
        if (cleanBody.contains("amazon", true)) return "Amazon"
        if (cleanBody.contains("netflix", true)) return "Netflix"
        if (cleanBody.contains("spotify", true)) return "Spotify"
        if (cleanBody.contains("recharge", true) || cleanBody.contains("jio", true) || cleanBody.contains("airtel", true)) return "Mobile Recharge"

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

    private fun getCategory(merchant: String): String {
        val name = merchant.lowercase()
        return when {
            name.contains("zomato") || name.contains("swiggy") || name.contains("burger") || name.contains("pizza") || name.contains("chai") -> "Food"
            name.contains("uber") || name.contains("ola") || name.contains("rapido") || name.contains("fuel") || name.contains("petrol") -> "Travel"
            name.contains("recharge") || name.contains("jio") || name.contains("bill") || name.contains("electricity") || name.contains("paytm") -> "Bills"
            name.contains("amazon") || name.contains("flipkart") || name.contains("myntra") || name.contains("shop") -> "Shopping"
            name.contains("netflix") || name.contains("spotify") || name.contains("movie") -> "Entertainment"
            else -> "Other"
        }
    }

    private fun setupPieChartStyle() {
        pieChart.apply {
            isDrawHoleEnabled = true
            setHoleColor(Color.TRANSPARENT)
            setTransparentCircleColor(Color.WHITE)
            setTransparentCircleAlpha(110)
            holeRadius = 58f
            transparentCircleRadius = 61f
            description.isEnabled = false
            legend.isEnabled = false
            setDrawEntryLabels(false)
            setUsePercentValues(true)
        }
    }

    private fun updatePieChart(categoryMap: Map<String, Double>) {
        if (categoryMap.isEmpty()) {
            pieChart.clear()
            pieChart.centerText = "No Data"
            return
        }
        val entries = categoryMap.map { PieEntry(it.value.toFloat(), it.key) }
        val dataSet = PieDataSet(entries, "")
        dataSet.colors = CHART_COLORS
        dataSet.sliceSpace = 3f
        dataSet.selectionShift = 5f
        val data = PieData(dataSet)
        data.setValueTextSize(12f)
        data.setValueTextColor(Color.WHITE)
        pieChart.data = data
        pieChart.centerText = "Spending"
        pieChart.setCenterTextSize(16f)
        pieChart.animateY(1400, Easing.EaseInOutQuad)
        pieChart.invalidate()
    }
}