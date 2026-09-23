package com.michaldrabik.ui_progress_movies.calendar.helpers.sorter

import com.michaldrabik.ui_model.Movie

interface CalendarSorter {
  fun sort(): Comparator<Movie>
}
