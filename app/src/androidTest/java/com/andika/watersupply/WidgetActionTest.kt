package com.andika.watersupply

import androidx.glance.GlanceId
import androidx.glance.action.actionParametersOf
import androidx.test.platform.app.InstrumentationRegistry
import com.andika.watersupply.widget.AddGlass
import com.andika.watersupply.widget.AddHalf
import com.andika.watersupply.widget.UndoLastDrink
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WidgetActionTest {
    @Test fun widgetActionsUseSamePersistentRepository() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = context.waterApp.repository
        val date = LocalDate.now().toString()
        val before = repository.dao.onDate(date)
        val glanceId = object : GlanceId {}
        try {
            AddGlass().onAction(context, glanceId, actionParametersOf())
            AddHalf().onAction(context, glanceId, actionParametersOf())
            assertEquals(before.sumOf { it.units } + 3, repository.today().first)
            UndoLastDrink().onAction(context, glanceId, actionParametersOf())
            assertEquals(before.sumOf { it.units } + 2, repository.today().first)
            UndoLastDrink().onAction(context, glanceId, actionParametersOf())
            assertEquals(before.sumOf { it.units }, repository.today().first)
            // A record created through the shared application repository is also undoable.
            repository.record(1)
            UndoLastDrink().onAction(context, glanceId, actionParametersOf())
            assertEquals(before.sumOf { it.units }, repository.today().first)
            assertEquals(before.map { it.id }.toSet(), repository.drinks.first()
                .filter { it.localDate == date }.map { it.id }.toSet())
        } finally {
            val original = before.map { it.id }.toSet()
            repository.dao.onDate(date).filter { it.id !in original }.forEach { repository.delete(it.id) }
            context.waterApp.refresh()
        }
    }
}
