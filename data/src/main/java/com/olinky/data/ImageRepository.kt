package com.olinky.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Provides access to stored disk image metadata.
 */
class ImageRepository {
    private val images = MutableStateFlow<List<StoredImage>>(emptyList())

    fun observeImages(): Flow<List<StoredImage>> = images

    fun addImage(image: StoredImage) {
        images.value = images.value + image
    }
}

data class StoredImage(
    val path: String,
    val label: String,
    val sizeBytes: Long,
    val bootable: Boolean
)
