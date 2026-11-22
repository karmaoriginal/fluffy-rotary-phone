package app.lumora.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.favoritesDataStore: DataStore<Preferences> by preferencesDataStore(name = "lumora_favorites")

/** Favourite ids of the online catalog (local favourites live in Room). */
class StoreFavoritesRepository(context: Context) {

    private val store = context.applicationContext.favoritesDataStore

    val favorites: Flow<Set<String>> = store.data.map { it[KEY] ?: emptySet() }

    suspend fun toggle(id: String) {
        store.edit { prefs ->
            val current = prefs[KEY] ?: emptySet()
            prefs[KEY] = if (id in current) current - id else current + id
        }
    }

    suspend fun clear() {
        store.edit { it.remove(KEY) }
    }

    private companion object {
        val KEY = stringSetPreferencesKey("store_favorites")
    }
}
