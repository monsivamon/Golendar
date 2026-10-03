package com.monsivamon.golender.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

// アプリ設定用 DataStore を Context 拡張として提供する
val Context.dataStore: DataStore<Preferences> by preferencesDataStore("golender_settings")