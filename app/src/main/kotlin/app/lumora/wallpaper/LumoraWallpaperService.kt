package app.lumora.wallpaper

import android.service.wallpaper.WallpaperService

class LumoraWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = Engine()

    private inner class Engine : WallpaperService.Engine()
}
