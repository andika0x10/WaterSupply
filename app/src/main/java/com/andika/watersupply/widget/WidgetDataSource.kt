package com.andika.watersupply.widget

import android.content.Context
import com.andika.watersupply.data.currentDateFlow
import com.andika.watersupply.data.WaterDao
import com.andika.watersupply.waterApp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import java.time.LocalDate

internal interface WidgetDataSource {
    suspend fun read(context: Context): WidgetProgress
    fun observe(context: Context): Flow<WidgetProgress>
}

internal object RoomWidgetDataSource : WidgetDataSource {
    override suspend fun read(context: Context): WidgetProgress {
        val (total, target) = context.waterApp.repository.today()
        return WidgetProgress(total, target, total > 0)
    }

    override fun observe(context: Context): Flow<WidgetProgress> =
        observeWidgetProgress(context.waterApp.repository.dao, context.currentDateFlow())
}

@OptIn(ExperimentalCoroutinesApi::class)
internal fun observeWidgetProgress(dao: WaterDao, dates: Flow<LocalDate>): Flow<WidgetProgress> =
    dates.flatMapLatest { date ->
        combine(dao.observeTotal(date.toString()), dao.observeTarget(date.toString())) { total, target ->
            WidgetProgress(total, target ?: 16, total > 0)
        }
    }.distinctUntilChanged()
