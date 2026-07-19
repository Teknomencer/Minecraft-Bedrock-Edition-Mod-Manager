package com.example

import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

object ModScanner {
    private val TARGET_EXTENSIONS = setOf("mcworld", "mcaddon", "mctemplate", "mcpack")

    suspend fun scanForMods(): List<ModFile> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ModFile>()
        
        // Root external storage directory (usually /storage/emulated/0)
        val rootDir = Environment.getExternalStorageDirectory()
        if (rootDir != null && rootDir.exists() && rootDir.isDirectory) {
            scanDirectoryRecursively(rootDir, results)
        }
        
        // Fallback or double check: explicitly scan standard downloads directory if not fully covered
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (downloadsDir != null && downloadsDir.exists() && downloadsDir.isDirectory) {
            // Check if some files in Downloads were missed or if we just want to ensure Downloads is scanned
            val downloadsFiles = mutableListOf<ModFile>()
            scanDirectoryRecursively(downloadsDir, downloadsFiles)
            for (file in downloadsFiles) {
                if (results.none { it.file.absolutePath == file.file.absolutePath }) {
                    results.add(file)
                }
            }
        }
        
        results
    }

    private fun scanDirectoryRecursively(dir: File, results: MutableList<ModFile>) {
        val files = try {
            dir.listFiles()
        } catch (e: Exception) {
            null
        } ?: return

        for (file in files) {
            if (file.isDirectory) {
                val name = file.name
                // Skip system files, caches, Android OS folders, and hidden files/directories to maximize speed
                if (name.startsWith(".") || 
                    name.equals("Android", ignoreCase = true) || 
                    name.equals("LOST.DIR", ignoreCase = true) ||
                    name.equals("System Volume Information", ignoreCase = true)) {
                    continue
                }
                scanDirectoryRecursively(file, results)
            } else {
                val ext = file.extension.lowercase(Locale.getDefault())
                if (TARGET_EXTENSIONS.contains(ext)) {
                    results.add(
                        ModFile(
                            file = file,
                            name = file.name,
                            size = file.length(),
                            lastModified = file.lastModified(),
                            extension = ext
                        )
                    )
                }
            }
        }
    }
}
