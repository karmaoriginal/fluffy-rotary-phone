package app.lumora.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.lumora.AppContainer
import app.lumora.ui.library.LibraryViewModel
import app.lumora.ui.preview.PreviewViewModel
import app.lumora.ui.settings.SettingsViewModel
import app.lumora.ui.store.StoreViewModel

/** Small factory helpers so ViewModels stay constructor-injected without extra frameworks. */
object ViewModelFactories {

    fun store(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            StoreViewModel(
                catalogRepository = container.catalog,
                libraryRepository = container.library,
                downloads = container.downloads,
                favoritesRepository = container.favorites,
            )
        }
    }

    fun library(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            LibraryViewModel(
                libraryRepository = container.library,
                settingsRepository = container.settings,
                tracker = container.enhancementTracker,
                downloads = container.downloads,
                applier = container.wallpaperApplier,
            )
        }
    }

    fun preview(container: AppContainer, source: String, id: String): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                PreviewViewModel(
                    container = container,
                    source = source,
                    id = id,
                )
            }
        }

    fun settings(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer { SettingsViewModel(container) }
    }
}
