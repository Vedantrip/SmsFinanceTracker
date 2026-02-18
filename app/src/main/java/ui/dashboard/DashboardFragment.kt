package com.example.smsfinancetracker.ui.dashboard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
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
    private lateinit var layoutEmptyState: View
    private lateinit var tvMonthYear: TextView
    private lateinit var btnPrevMonth: ImageButton
    private lateinit var btnNextMonth: ImageButton

    // Search Views
    private lateinit var btnSearch: ImageButton
    private lateinit var searchLayout: View
    private lateinit var etSearch: TextInputEditText

    // --- Config ---
    private var monthlyBudget = 5000.0
    private var selectedCalendar = Calendar.getInstance()
    private var currentMonthTransactions: MutableList<TransactionEntity> = mutableListOf()

    // Neon / Luxury Palette
    private val CHART_COLORS = listOf(
        Color.parseColor("#CCFF00"), // Neon Lime
        Color.parseColor("#00E676"), // Bright Green
        Color.parseColor("#00B0FF"), // Electric Blue
        Color.parseColor("#651FFF"), // Deep Purple
        Color.parseColor("#FF4081"), // Hot Pink
        Color.parseColor("#E0E0E0")  // Light Grey
    )

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean -> if (isGranted) readInboxAndSaveToDb() }

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
        tvMonthYear = view.findViewById(R.id.tvMonthYear)
        btnPrevMonth = view.findViewById(R.id.btnPrevMonth)
        btnNextMonth = view.findViewById(R.id.btnNextMonth)
        btnSearch = view.findViewById(R.id.btnSearch)
        searchLayout = view.findViewById(R.id.searchLayout)
        etSearch = view.findViewById(R.id.etSearch)

        rvTransactions.layoutManager = LinearLayoutManager(requireContext())
        rvTransactions.adapter = TransactionAdapter(emptyList())

        setupSwipeToDelete()
        loadBudgetFromPrefs()
        setupPieChartStyle()

        // --- START LIVE DATA STREAM ---
        observeDatabase()
        // -----------------------------

        // Listeners
        fabAdd.setOnClickListener {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            showAddTransactionDialog()
        }

        tvBudgetLeft.setOnClickListener {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            showSetBudgetDialog()
        }

        // Search Toggle
        btnSearch.setOnClickListener {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            if (searchLayout.visibility == View.VISIBLE) {
                searchLayout.visibility = View.GONE
                etSearch.text?.clear()
            } else {
                searchLayout.visibility = View.VISIBLE
                etSearch.requestFocus()
            }
        }

        // Search Logic
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { filterTransactions(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Month Navigation
        btnPrevMonth.setOnClickListener {
            selectedCalendar.add(Calendar.MONTH, -1)
            updateDateText()
            observeDatabase() // Re-trigger filter
        }
        btnNextMonth.setOnClickListener {
            selectedCalendar.add(Calendar.MONTH, 1)
            updateDateText()
            observeDatabase() // Re-trigger filter
        }

        updateDateText()
        checkPermissionAndLoad()

        return view
    }

    // ================= REAL-TIME DATA LOGIC =================

    private fun observeDatabase() {
        val dao = AppDatabase.get(requireContext()).transactionDao()
        // Collect Flow: Updates automatically whenever DB changes
        lifecycleScope.launch {
            dao.getAll().collect { allTransactions ->
                processAndDisplayData(allTransactions)
            }
        }
    }

    private fun processAndDisplayData(all: List<TransactionEntity>) {
        val targetMonth = selectedCalendar.get(Calendar.MONTH)
        val targetYear = selectedCalendar.get(Calendar.YEAR)

        // 1. Filter by Date (Include BOTH Income & Expense)
        val filteredList = all.filter {
            val txnDate = Calendar.getInstance()
            txnDate.timeInMillis = it.timestamp
            txnDate.get(Calendar.MONTH) == targetMonth &&
                    txnDate.get(Calendar.YEAR) == targetYear
        }

        // 2. Calculate Totals (Separate Logic)
        var totalSpent = 0.0
        val categoryMap = mutableMapOf<String, Double>()

        filteredList.forEach {
            // Only count DEBITS for the "Total Spent" and Chart
            if (it.type == "DEBIT") {
                totalSpent += it.amount
                val cat = getCategory(it.merchant)
                categoryMap[cat] = categoryMap.getOrDefault(cat, 0.0) + it.amount
            }
        }

        // 3. Store full list for Search/Adapter
        currentMonthTransactions = filteredList.sortedByDescending { it.timestamp }.toMutableList()

        // 4. Update UI
        tvTotal.text = "₹${"%.2f".format(totalSpent)}"

        // Budget Logic (Based on Spent only)
        val budget = if (monthlyBudget == 0.0) 1.0 else monthlyBudget
        val percentage = (totalSpent / budget) * 100
        progressBar.progress = percentage.toInt()

        val remaining = budget - totalSpent
        if (remaining > 0) {
            tvBudgetLeft.text = "₹${"%.0f".format(remaining)} left"
            tvSmartInsight.text = "Safe"
            tvSmartInsight.setTextColor(Color.parseColor("#CCFF00")) // Neon Green
        } else {
            tvBudgetLeft.text = "Over by ₹${"%.0f".format(Math.abs(remaining))}"
            tvSmartInsight.text = "Alert"
            tvSmartInsight.setTextColor(Color.parseColor("#FF4081")) // Hot Pink
        }

        // Update Charts & Lists
        updatePieChart(categoryMap)

        // If search is active, don't reset list, let filter handle it
        if (etSearch.text.isNullOrEmpty()) {
            updateAdapter(currentMonthTransactions)
        } else {
            filterTransactions(etSearch.text.toString())
        }
    }

    private fun filterTransactions(query: String) {
        val lowerQuery = query.lowercase()
        val filtered = currentMonthTransactions.filter {
            it.merchant.lowercase().contains(lowerQuery) ||
                    it.amount.toString().contains(lowerQuery)
        }
        updateAdapter(filtered)
    }

    private fun updateAdapter(list: List<TransactionEntity>) {
        if (list.isEmpty()) {
            rvTransactions.visibility = View.GONE
            layoutEmptyState.visibility = View.VISIBLE
        } else {
            rvTransactions.visibility = View.VISIBLE
            layoutEmptyState.visibility = View.GONE
            rvTransactions.adapter = TransactionAdapter(list)
        }
    }

    // ================= OPERATIONS (ADD/DELETE) =================

    private fun saveManualTransaction(amount: Double, description: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val transaction = TransactionEntity(
                merchant = description,
                amount = amount,
                timestamp = System.currentTimeMillis(),
                type = "DEBIT" // Manual entry default
            )
            AppDatabase.get(requireContext()).transactionDao().insert(transaction)
        }
    }

    private fun deleteTransaction(transaction: TransactionEntity) {
        lifecycleScope.launch(Dispatchers.IO) {
            AppDatabase.get(requireContext()).transactionDao().delete(transaction)
            withContext(Dispatchers.Main) {
                Snackbar.make(rvTransactions, "Transaction deleted", Snackbar.LENGTH_LONG)
                    .setAction("UNDO") { undoDelete(transaction) }
                    .setAnchorView(fabAdd)
                    .show()
            }
        }
    }

    private fun undoDelete(transaction: TransactionEntity) {
        lifecycleScope.launch(Dispatchers.IO) {
            AppDatabase.get(requireContext()).transactionDao().insert(transaction)
        }
    }

    // ================= SMS PARSING (SMART) =================

    private fun readInboxAndSaveToDb() {
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = AppDatabase.get(requireContext()).transactionDao()
            try {
                val cursor = requireContext().contentResolver.query(
                    android.net.Uri.parse("content://sms/inbox"), null, null, null, null
                )
                cursor?.use {
                    val bodyIndex = it.getColumnIndex("body")
                    val dateIndex = it.getColumnIndex("date")

                    while (it.moveToNext()) {
                        val body = it.getString(bodyIndex) ?: ""
                        val date = it.getLong(dateIndex)

                        // 1. Check for INCOME
                        if (body.contains("credited", true) || body.contains("received", true) || body.contains("deposited", true)) {
                            val amount = extractAmount(body)
                            if (amount > 0) {
                                // Simple check to avoid duplicates (naive)
                                val transaction = TransactionEntity(merchant = "Income", amount = amount, timestamp = date, type = "CREDIT")
                                dao.insert(transaction)
                            }
                        }
                        // 2. Check for EXPENSE
                        else if (body.contains("Sent Rs.", true) || body.contains("debited", true) || body.contains("spent", true) || body.contains("paid", true)) {
                            val amount = extractAmount(body)
                            if (amount > 0) {
                                val merchant = extractMerchant(body)
                                val transaction = TransactionEntity(merchant = merchant, amount = amount, timestamp = date, type = "DEBIT")
                                dao.insert(transaction)
                            }
                        }
                    }
                }
            } catch (e: Exception) { Log.e("Dashboard", "Error reading SMS: ${e.message}") }
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

    // ================= VISUALS =================

    private fun updateDateText() {
        val sdf = SimpleDateFormat("MMM yyyy", Locale.getDefault())
        tvMonthYear.text = sdf.format(selectedCalendar.time).uppercase()
    }

    private fun setupPieChartStyle() {
        pieChart.apply {
            isDrawHoleEnabled = true
            setHoleColor(Color.TRANSPARENT)
            setTransparentCircleColor(Color.BLACK)
            setTransparentCircleAlpha(0)
            holeRadius = 70f
            transparentCircleRadius = 70f
            description.isEnabled = false
            legend.isEnabled = false
            setDrawEntryLabels(false)
            setUsePercentValues(true)
            setTouchEnabled(true)
        }
    }

    private fun updatePieChart(categoryMap: Map<String, Double>) {
        if (categoryMap.isEmpty()) {
            pieChart.clear()
            return
        }
        val entries = categoryMap.map { PieEntry(it.value.toFloat(), it.key) }
        val dataSet = PieDataSet(entries, "")
        dataSet.colors = CHART_COLORS
        dataSet.sliceSpace = 3f
        dataSet.selectionShift = 5f
        val data = PieData(dataSet)
        data.setValueTextSize(0f)
        pieChart.data = data
        pieChart.animateY(1400, Easing.EaseInOutQuad)
        pieChart.invalidate()
    }

    private fun setupSwipeToDelete() {
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
                val itemView = viewHolder.itemView
                val background = ColorDrawable(Color.parseColor("#B71C1C")) // Red
                val icon = ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_menu_delete)
                background.setBounds(itemView.right + dX.toInt(), itemView.top, itemView.right, itemView.bottom)
                background.draw(c)
                icon?.let {
                    it.setTint(Color.WHITE)
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
                if (position < currentMonthTransactions.size) {
                    val transactionToDelete = currentMonthTransactions[position]
                    view?.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    deleteTransaction(transactionToDelete)
                }
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(rvTransactions)
    }

    // ================= DIALOGS & PREFS =================

    private fun loadBudgetFromPrefs() {
        val prefs = requireContext().getSharedPreferences("FinancePrefs", Context.MODE_PRIVATE)
        monthlyBudget = prefs.getFloat("USER_BUDGET", 5000f).toDouble()
    }

    private fun saveBudgetToPrefs(newBudget: Double) {
        val prefs = requireContext().getSharedPreferences("FinancePrefs", Context.MODE_PRIVATE)
        prefs.edit().putFloat("USER_BUDGET", newBudget.toFloat()).apply()
        monthlyBudget = newBudget
        observeDatabase()
    }

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

    private fun checkPermissionAndLoad() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            readInboxAndSaveToDb()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.READ_SMS)
        }
    }
}