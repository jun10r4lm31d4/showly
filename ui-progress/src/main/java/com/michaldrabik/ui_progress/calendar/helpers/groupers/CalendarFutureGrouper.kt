package com.michaldrabik.ui_progress.calendar.helpers.groupers

import com.michaldrabik.common.extensions.nowUtc
import com.michaldrabik.common.extensions.toLocalZone
import com.michaldrabik.ui_model.CalendarMode
import com.michaldrabik.ui_progress.R
import com.michaldrabik.ui_progress.calendar.recycler.CalendarListItem
import java.time.DayOfWeek
import java.time.Month
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.TemporalAdjusters.next
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarFutureGrouper @Inject constructor() : CalendarGrouper {

  override fun groupByTime(items: List<CalendarListItem.Episode>): List<CalendarListItem> {
    val nowDays = nowUtc().toLocalZone().truncatedTo(DAYS)

    val itemsMap = mutableMapOf<Int, MutableList<CalendarListItem>>()
      .apply {
        put(com.michaldrabik.ui_base.R.string.textToday, mutableListOf())
        put(com.michaldrabik.ui_base.R.string.textTomorrow, mutableListOf())
        put(com.michaldrabik.ui_base.R.string.textThisWeek, mutableListOf())
        put(com.michaldrabik.ui_base.R.string.textNextWeek, mutableListOf())
        put(com.michaldrabik.ui_base.R.string.textThisMonth, mutableListOf())
        put(com.michaldrabik.ui_base.R.string.textNextMonth, mutableListOf())
        put(com.michaldrabik.ui_base.R.string.textThisYear, mutableListOf())
        put(com.michaldrabik.ui_base.R.string.textLater, mutableListOf())
      }

    items.forEach { item ->
      val itemDays = item.episode.firstAired
        ?.toLocalZone()
        ?.truncatedTo(DAYS)
      when {
        itemDays?.isEqual(nowDays) == true -> {
          itemsMap[com.michaldrabik.ui_base.R.string.textToday]?.add(item)
        }
        itemDays?.isEqual(nowDays.plusDays(1)) == true -> {
          itemsMap[com.michaldrabik.ui_base.R.string.textTomorrow]?.add(item)
        }
        itemDays?.isBefore(nowDays.with(next(DayOfWeek.MONDAY))) == true -> {
          itemsMap[com.michaldrabik.ui_base.R.string.textThisWeek]?.add(item)
        }
        itemDays?.isBefore(nowDays.plusWeeks(1).with(next(DayOfWeek.MONDAY))) == true -> {
          itemsMap[com.michaldrabik.ui_base.R.string.textNextWeek]?.add(item)
        }
        itemDays?.month == nowDays.month && itemDays?.year == nowDays.year -> {
          itemsMap[com.michaldrabik.ui_base.R.string.textThisMonth]?.add(item)
        }
        (itemDays?.monthValue == (nowDays.monthValue + 1) && itemDays.year == nowDays.year) ||
          (itemDays?.month == Month.JANUARY && nowDays.month == Month.DECEMBER) -> {
          itemsMap[com.michaldrabik.ui_base.R.string.textNextMonth]?.add(item)
        }
        itemDays?.year == nowDays.year -> {
          itemsMap[com.michaldrabik.ui_base.R.string.textThisYear]?.add(item)
        }
        else -> {
          itemsMap[com.michaldrabik.ui_base.R.string.textLater]?.add(item)
        }
      }
    }

    return itemsMap.entries.fold(mutableListOf()) { acc, entry ->
      acc.apply {
        if (entry.value.isNotEmpty()) {
          add(CalendarListItem.Header.create(entry.key, CalendarMode.PRESENT_FUTURE))
          addAll(entry.value)
        }
      }
    }
  }
}
