package com.spendwise.backend;

import java.util.HashMap;
import java.util.Map;

/** Firestore POJO for users/{uid}/settings/budget. */
public class BudgetConfig {
    private double monthlyBudget = 30000;
    private Map<String, Double> categoryBudgets = new HashMap<>();

    public BudgetConfig() { }

    public double getMonthlyBudget() { return monthlyBudget; }
    public void setMonthlyBudget(double monthlyBudget) { this.monthlyBudget = monthlyBudget; }

    public Map<String, Double> getCategoryBudgets() { return categoryBudgets; }
    public void setCategoryBudgets(Map<String, Double> categoryBudgets) { this.categoryBudgets = categoryBudgets; }
}
