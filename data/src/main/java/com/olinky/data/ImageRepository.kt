package com.olinky.data

import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Provides access to stored disk image metadata.
 */
class ImageRepository {
    private val images = MutableStateFlow<List<StoredImage>>(emptyList())

    init {
        if (images.value.isEmpty()) {
            images.value = listOf(
                StoredImage(
                    label = "Ubuntu 24.04 LTS",
                    path = "/sdcard/Download/ubuntu-24.04-desktop.iso",
                    sizeBytes = 4_500_000_000,
                    bootable = true
                ),
                StoredImage(
                    label = "Ventoy Toolkit",
                    path = "/sdcard/Download/ventoy-toolkit.img",
                    sizeBytes = 512_000_000,
                    bootable = true
                ),
                StoredImage(
                    label = "Data Rescue Kit",
                    path = "/sdcard/oLinky/rescue.iso",
                    sizeBytes = 1_200_000_000,
                    bootable = true
                )
            )
        }
    }

    fun observeImages(): Flow<List<StoredImage>> = images

    fun addImage(image: StoredImage) {
        images.update { current ->
            val filtered = current.filterNot { it.id == image.id }
            filtered + image
        }
    }

    fun removeImage(id: String) {
        images.update { current -> current.filterNot { it.id == id } }
    }

    fun getImage(id: String): StoredImage? = images.value.firstOrNull { it.id == id }
}

data class StoredImage(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val path: String,
    val sizeBytes: Long,
    val bootable: Boolean
)
