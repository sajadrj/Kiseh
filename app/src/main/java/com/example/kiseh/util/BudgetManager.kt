package com.example.kiseh.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.budgetDataStore: DataStore<Preferences> by preferencesDataStore(name = "budgets")

class BudgetManager(private val context: Context) {

    // ذخیره سقف بودجه برای یک دسته‌بندی و بازه زمانی خاص (روزانه، ماهانه، سالانه)
    suspend fun saveBudget(category: String, timeFrame: String, amount: Double) {
        val key = doublePreferencesKey("${category}_$timeFrame")
        context.budgetDataStore.edit { preferences ->
            preferences[key] = amount
        }
    }

    // دریافت بودجه یک دسته و بازه زمانی به صورت Flow
    fun getBudget(category: String, timeFrame: String): Flow<Double> {
        val key = doublePreferencesKey("${category}_$timeFrame")
        return context.budgetDataStore.data.map { preferences ->
            preferences[key] ?: 0.0
        }
    }
}
