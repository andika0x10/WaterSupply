package com.andika.watersupply

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andika.watersupply.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate

data class WaterState(val drinks: List<Drink> = emptyList(), val targets: List<DailyTarget> = emptyList(),
    val preferences: Preferences = Preferences(), val date: LocalDate = LocalDate.now(), val loading: Boolean = true) {
    val today get() = drinks.filter { it.localDate == date.toString() }
    val total get() = today.sumOf { it.units }
    val target get() = targetFor(targets, date)
}
class WaterViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as WaterApplication
    private val repo = app.repository
    private val day = flow { while (true) { emit(LocalDate.now()); delay(15_000) } }
    val state = combine(repo.drinks, repo.targets, repo.preferences, day) { d, t, p, date -> WaterState(d, t, p, date, false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WaterState())
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    private fun perform(block: suspend () -> Unit) { viewModelScope.launch {
        try { block(); app.refresh() } catch (e: Exception) {
            if (e is CancellationException) throw e
            android.util.Log.e("WaterSupply", "Operation failed", e)
            messages.emit(if (e is IllegalArgumentException) e.message ?: "Periksa nilai yang dimasukkan."
                else "Perubahan belum terselesaikan. Periksa catatanmu dan coba lagi.")
        }
    } }
    fun add(units: Int, completed: (Long) -> Unit) = perform { completed(repo.record(units)) }
    fun save(units: Int, timestamp: Long, id: Long) = perform { repo.record(units, timestamp, id) }
    fun delete(id: Long) = perform { repo.delete(id) }
    fun target(units: Int) = perform { repo.setTarget(units) }
    fun preferences(p: Preferences) = perform { repo.savePreferences(p) }
    fun refresh() = perform { }
}
