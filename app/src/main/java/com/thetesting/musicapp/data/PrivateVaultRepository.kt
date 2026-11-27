package com.thetesting.musicapp.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

data class VaultItem(
    val id: String,
    val name: String,
    val filePath: String,
    val sizeBytes: Long,
    val addedDateMs: Long,
    val isVideo: Boolean,
    val mimeType: String
)

class PrivateVaultRepository(private val context: Context) {

    private val vaultDir = File(context.filesDir, "private_vault").apply { mkdirs() }
    private val videosDir = File(vaultDir, "videos").apply { mkdirs() }
    private val filesDir = File(vaultDir, "files").apply { mkdirs() }
    private val metaFile = File(vaultDir, "vault_items.json")
    private val pinFile = File(vaultDir, "vault_pin.dat")

    fun isPinSet(): Boolean {
        return pinFile.exists() && pinFile.readText().isNotBlank()
    }

    fun verifyPin(pin: String): Boolean {
        if (!isPinSet()) return false
        val storedHash = pinFile.readText().trim()
        return hashPin(pin) == storedHash
    }

    fun setPin(pin: String) {
        val hash = hashPin(pin)
        pinFile.writeText(hash)
    }

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("mysmile_salt_$pin".toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun getItems(): List<VaultItem> = withContext(Dispatchers.IO) {
        if (!metaFile.exists()) return@withContext emptyList()
        try {
            val arr = JSONArray(metaFile.readText())
            val list = mutableListOf<VaultItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val path = obj.optString("filePath")
                val file = File(path)
                if (file.exists()) {
                    list.add(
                        VaultItem(
                            id = obj.optString("id"),
                            name = obj.optString("name"),
                            filePath = path,
                            sizeBytes = obj.optLong("sizeBytes", file.length()),
                            addedDateMs = obj.optLong("addedDateMs", file.lastModified()),
                            isVideo = obj.optBoolean("isVideo", false),
                            mimeType = obj.optString("mimeType", "application/octet-stream")
                        )
                    )
                }
            }
            list.sortedByDescending { it.addedDateMs }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun importItem(uri: Uri, isVideo: Boolean): VaultItem? = withContext(Dispatchers.IO) {
        try {
            val originalName = queryFileName(uri) ?: if (isVideo) "video_${System.currentTimeMillis()}.mp4" else "file_${System.currentTimeMillis()}"
            val ext = originalName.substringAfterLast('.', "")
            val mimeType = context.contentResolver.getType(uri) ?: run {
                if (ext.isNotEmpty()) MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase()) ?: "application/octet-stream"
                else "application/octet-stream"
            }

            val targetDir = if (isVideo) videosDir else filesDir
            val safeId = UUID.randomUUID().toString().take(8)
            val cleanName = originalName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val targetFile = File(targetDir, "${safeId}_$cleanName")

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null

            val newItem = VaultItem(
                id = safeId,
                name = originalName,
                filePath = targetFile.absolutePath,
                sizeBytes = targetFile.length(),
                addedDateMs = System.currentTimeMillis(),
                isVideo = isVideo,
                mimeType = mimeType
            )

            val currentItems = getItems().toMutableList()
            currentItems.add(0, newItem)
            saveItemsList(currentItems)

            newItem
        } catch (_: Exception) {
            null
        }
    }

    suspend fun deleteItem(item: VaultItem) = withContext(Dispatchers.IO) {
        val file = File(item.filePath)
        if (file.exists()) {
            file.delete()
        }
        val currentItems = getItems().filterNot { it.id == item.id }
        saveItemsList(currentItems)
    }

    private fun saveItemsList(items: List<VaultItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("filePath", item.filePath)
                put("sizeBytes", item.sizeBytes)
                put("addedDateMs", item.addedDateMs)
                put("isVideo", item.isVideo)
                put("mimeType", item.mimeType)
            }
            arr.put(obj)
        }
        metaFile.writeText(arr.toString())
    }

    private fun queryFileName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
