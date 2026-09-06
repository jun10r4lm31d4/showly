package com.michaldrabik.repository.settings

import android.content.SharedPreferences
import com.michaldrabik.repository.utilities.LongPreference
import com.michaldrabik.repository.utilities.StringPreference
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class SettingsSyncRepository @Inject constructor(
  @Named("syncPreferences") private var preferences: SharedPreferences,
) {

  companion object Key {
    private const val ACTIVITY_SHOWS_HIDDEN_AT = "ACTIVITY_SHOWS_HIDDEN_AT"
    private const val ACTIVITY_SHOWS_WATCHLISTED_AT = "ACTIVITY_SHOWS_WATCHLISTED_AT"
    private const val ACTIVITY_MOVIES_WATCHED_AT = "ACTIVITY_MOVIES_WATCHED_AT"
    private const val ACTIVITY_MOVIES_HIDDEN_AT = "ACTIVITY_MOVIES_HIDDEN_AT"
    private const val ACTIVITY_MOVIES_WATCHLISTED_AT = "ACTIVITY_MOVIES_WATCHLISTED_AT"
    private const val ACTIVITY_LISTS_UPDATED_AT = "ACTIVITY_LISTS_UPDATED_AT"
    private const val ACTIVITY_EPISODES_WATCHED_AT = "ACTIVITY_EPISODES_WATCHED_AT"
    private const val ACTIVITY_SCROB_HISTORY_SYNCED_AT = "ACTIVITY_SCROB_HISTORY_SYNCED_AT"
  }

  var activityShowsHiddenAt: String by StringPreference(preferences, ACTIVITY_SHOWS_HIDDEN_AT, "")
  var activityShowsWatchlistedAt: String by StringPreference(preferences, ACTIVITY_SHOWS_WATCHLISTED_AT, "")

  var activityMoviesWatchedAt by StringPreference(preferences, ACTIVITY_MOVIES_WATCHED_AT, "")
  var activityMoviesHiddenAt by StringPreference(preferences, ACTIVITY_MOVIES_HIDDEN_AT, "")
  var activityMoviesWatchlistedAt by StringPreference(preferences, ACTIVITY_MOVIES_WATCHLISTED_AT, "")

  var activityEpisodesWatchedAt by StringPreference(preferences, ACTIVITY_EPISODES_WATCHED_AT, "")
  var activityListsUpdatedAt by StringPreference(preferences, ACTIVITY_LISTS_UPDATED_AT, "")

  // Scrob has no last_activities-style endpoint, so a high-water mark on watched_at
  // (epoch millis) is used instead to only import history newer than the last run.
  var activityScrobHistorySyncedAt by LongPreference(preferences, ACTIVITY_SCROB_HISTORY_SYNCED_AT, 0L)
}
