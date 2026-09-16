package com.example.stockflow.ui.common

import android.view.View
import android.widget.TextView
import com.example.stockflow.R
import com.example.stockflow.data.sync.SyncStatus

/**
 * Compact sync status banner (pending / syncing / failed), similar to [OfflineBanner].
 */
object SyncStatusBanner {
    fun show(banner: TextView?, status: SyncStatus?) {
        if (banner == null) return
        if (status == null) {
            banner.visibility = View.GONE
            return
        }
        val text = when {
            status.isSyncing -> banner.context.getString(R.string.syncing)
            status.hasFailed -> {
                val base = banner.context.getString(R.string.sync_failed)
                if (status.lastError.isNullOrBlank()) base else "$base — ${status.lastError}"
            }
            status.hasPending -> banner.context.getString(
                R.string.pending_sync_count,
                status.pendingCount
            )
            else -> {
                banner.visibility = View.GONE
                return
            }
        }
        banner.visibility = View.VISIBLE
        banner.text = text
    }

    fun hide(banner: TextView?) {
        banner?.visibility = View.GONE
    }
}
