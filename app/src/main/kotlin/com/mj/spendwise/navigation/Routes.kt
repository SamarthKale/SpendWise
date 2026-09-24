package com.mj.spendwise.navigation

/** Every route string in one place (CLAUDE.md 8.2). */
object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"

    // Nested graphs (lab outcome 2: nested navigation graphs)
    const val MAIN_GRAPH = "main_graph"      // the 5 bottom-nav tabs
    const val EXPENSE_FLOW = "expense_flow"  // add/edit + detail

    // Bottom-nav tabs
    const val DASHBOARD = "main/dashboard"
    const val EXPENSES = "main/expenses"
    const val ANALYTICS = "main/analytics"
    const val MAP = "main/map"
    const val CHAT = "main/chat"

    // Argument routes
    const val ARG_SCAN = "scan"
    const val ARG_EDIT_ID = "editId"
    const val ARG_EXPENSE_ID = "expenseId"
    const val ADD_EXPENSE = "add_expense?scan={scan}&editId={editId}"
    const val EXPENSE_DETAIL = "expense_detail/{expenseId}"

    const val ALERTS = "alerts"
    const val SETTINGS = "settings"

    fun addExpense(scan: Boolean = false, editId: String? = null): String =
        "add_expense?scan=$scan" + (if (editId != null) "&editId=$editId" else "")

    fun expenseDetail(id: String) = "expense_detail/$id"

    val tabs = listOf(DASHBOARD, EXPENSES, ANALYTICS, MAP, CHAT)
}
