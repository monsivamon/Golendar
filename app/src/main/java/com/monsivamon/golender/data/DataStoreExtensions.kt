package com.monsivamon.golender.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

// DataStoreインスタンスをContext拡張で提供
val Context.dataStore: DataStore<Preferences> by preferencesDataStore("golender_settings")