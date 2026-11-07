package com.khizrim.dialer.telecom

import androidx.compose.ui.geometry.Rect

/**
 * Откуда вырастает экран звонка: прямоугольник нажатой кнопки в координатах окна.
 * Приложение кладёт его перед звонком, экран звонка забирает один раз.
 * Нет прямоугольника (входящий, звонок из уведомления) — экран спускается из капсулы наверху.
 */
object CallOrigin {
    @Volatile private var rect: Rect? = null
    @Volatile private var at = 0L

    fun set(r: Rect) {
        if (r.width <= 0f || r.height <= 0f) return
        rect = r
        at = android.os.SystemClock.elapsedRealtime()
    }

    /** Прямоугольник, если его положили недавно (звонок мог и не начаться). */
    fun take(): Rect? {
        val r = rect
        rect = null
        return r?.takeIf { android.os.SystemClock.elapsedRealtime() - at < 3_000 }
    }
}
