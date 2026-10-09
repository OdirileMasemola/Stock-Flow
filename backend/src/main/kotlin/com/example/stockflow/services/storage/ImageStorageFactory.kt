package com.example.stockflow.services.storage

import com.example.stockflow.config.AppConfig
import org.slf4j.LoggerFactory

object ImageStorageFactory {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun create(): ImageStorage {
        return when (AppConfig.storageProvider) {
            AppConfig.STORAGE_PROVIDER_SUPABASE -> {
                logger.info(
                    "Image storage provider: supabase (bucket={})",
                    AppConfig.supabaseStorageBucket
                )
                SupabaseImageStorage()
            }
            else -> {
                logger.info(
                    "Image storage provider: local (dir={})",
                    AppConfig.uploadsDir
                )
                LocalDiskImageStorage()
            }
        }
    }
}
