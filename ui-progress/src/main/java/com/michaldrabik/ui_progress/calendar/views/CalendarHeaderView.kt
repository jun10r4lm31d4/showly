package com.michaldrabik.ui_progress.calendar.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import androidx.core.view.updatePadding
import com.michaldrabik.ui_base.utilities.extensions.dimenToPx
import com.michaldrabik.ui_progress.R
import com.michaldrabik.ui_progress.calendar.recycler.CalendarListItem
import com.michaldrabik.ui_progress.databinding.ViewCalendarHeaderBinding

class CalendarHeaderView : LinearLayout {

  constructor(context: Context) : super(context)
  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

  private val binding = ViewCalendarHeaderBinding.inflate(LayoutInflater.from(context), this)

  init {
    layoutParams = LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    orientation = HORIZONTAL
    updatePadding(
      top = context.dimenToPx(com.michaldrabik.ui_base.R.dimen.spaceBig),
      bottom = context.dimenToPx(com.michaldrabik.ui_base.R.dimen.spaceTiny),
      left = context.dimenToPx(com.michaldrabik.ui_base.R.dimen.itemMarginHorizontal),
      right = context.dimenToPx(com.michaldrabik.ui_base.R.dimen.itemMarginHorizontal),
    )
  }

  fun bind(
    item: CalendarListItem.Header,
    position: Int,
  ) {
    with(binding) {
      calendarHeaderText.setText(item.textResId)
    }
    updatePadding(
      top = context.dimenToPx(if (position == 1) com.michaldrabik.ui_base.R.dimen.spaceMedium else com.michaldrabik.ui_base.R.dimen.spaceBig),
    )
  }
}
