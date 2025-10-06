package com.olinky.data

import java.io.File
import java.io.RandomAccessFile
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/**
 * Provides access to stored disk image metadata sourced from the configured library directory.
 */
class ImageRepository {

    private val images = MutableStateFlow<List<StoredImage>>(emptyList())

    fun observeImages(): Flow<List<StoredImage>> = images

    suspend fun refreshFromDirectory(directory: File?) {
        val snapshot = withContext(Dispatchers.IO) {
            if (directory == null || !directory.exists() || !directory.isDirectory) {
                emptyList()
            } else {
                directory.listFiles()
                    ?.filter { it.isFile && it.extension.lowercase(Locale.US) in SUPPORTED_EXTENSIONS }
                    ?.sortedByDescending { it.lastModified() }
                    ?.map { file ->
                        StoredImage(
                            id = file.absolutePath,
                            label = file.nameWithoutExtension,
                            path = file.absolutePath,
                            sizeBytes = file.length(),
                            bootable = file.extension.lowercase(Locale.US) in BOOTABLE_EXTENSIONS,
                            lastModifiedMillis = file.lastModified()
                        )
                    }
                    ?: emptyList()
            }
        }
        images.value = snapshot
    }

    suspend fun createBlankImage(targetDirectory: File, fileName: String, sizeBytes: Long): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!targetDirectory.exists()) {
                    targetDirectory.mkdirs()
                }
                require(targetDirectory.isDirectory) { "${targetDirectory.absolutePath} is not a directory" }
                val sanitizedName = fileName.trim().ifEmpty { "disk" }
                val file = File(targetDirectory, ensureIsoExtension(sanitizedName))
                if (file.exists()) {
                    throw IllegalStateException("File already exists: ${file.name}")
                }
                file.createNewFile()
                RandomAccessFile(file, "rw").use { raf ->
                    raf.setLength(sizeBytes)
                }
                file
            }
        }

    suspend fun deleteImage(filePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(filePath)
            if (file.exists()) {
                file.delete()
            }
            Unit
        }
    }

    companion object {
        private val SUPPORTED_EXTENSIONS = setOf("iso", "img", "bin", "raw", "vhd", "vhdx", "qcow2")
        private val BOOTABLE_EXTENSIONS = setOf("iso", "img", "raw", "vhd", "vhdx", "qcow2")

        private fun ensureIsoExtension(name: String): String {
            val lowered = name.lowercase(Locale.US)
            return if (lowered.endsWith(".iso") || lowered.endsWith(".img") || lowered.endsWith(".raw")) {
                name
            } else {
                "$name.iso"
            }
        }
    }
}

data class StoredImage(
    val id: String,
    val label: String,
    val path: String,
    val sizeBytes: Long,
    val bootable: Boolean,
    val lastModifiedMillis: Long
)
