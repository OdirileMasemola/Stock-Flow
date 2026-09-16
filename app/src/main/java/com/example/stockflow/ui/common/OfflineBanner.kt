package com.example.stockflow.ui.common

import android.view.View
import android.widget.TextView
import com.example.stockflow.R
import java.text.DateFormat
import java.util.Date

object OfflineBanner {
    fun show(banner: TextView?, fromCache: Boolean, cachedAt: Long?) {
        if (banner == null) return
        if (!fromCache) {
            banner.visibility = View.GONE
            return
        }
        banner.visibility = View.VISIBLE
        banner.text = if (cachedAt != null && cachedAt > 0L) {
            val time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(cachedAt))
            banner.context.getString(R.string.offline_cached_at, time)
        } else {
            banner.context.getString(R.string.offline_cached_banner)
        }
    }

    fun hide(banner: TextView?) {
        banner?.visibility = View.GONE
    }
}
