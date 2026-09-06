package com.michaldrabik.ui_settings.sections.scrob.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import com.michaldrabik.ui_settings.databinding.ViewScrobLoginBinding

class ScrobLoginView : LinearLayout {

  constructor(context: Context) : super(context)
  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

  private val binding = ViewScrobLoginBinding.inflate(LayoutInflater.from(context), this, true)

  val serverUrl: String
    get() = binding.scrobLoginUrlInput.text
      ?.toString()
      .orEmpty()

  val username: String
    get() = binding.scrobLoginUsernameInput.text
      ?.toString()
      .orEmpty()

  val password: String
    get() = binding.scrobLoginPasswordInput.text
      ?.toString()
      .orEmpty()
}
