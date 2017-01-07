package com.simplemobiletools.gallery.asynctasks

import android.content.Context
import android.database.Cursor
import android.os.AsyncTask
import android.provider.MediaStore
import com.simplemobiletools.commons.extensions.scanFiles
import com.simplemobiletools.gallery.R
import com.simplemobiletools.gallery.extensions.getHumanizedFilename
import com.simplemobiletools.gallery.extensions.getLongValue
import com.simplemobiletools.gallery.extensions.getStringValue
import com.simplemobiletools.gallery.helpers.*
import com.simplemobiletools.gallery.models.Directory
import com.simplemobiletools.gallery.models.Medium
import java.io.File
import java.util.*

class GetDirectoriesAsynctask(val context: Context, val isPickVideo: Boolean, val isPickImage: Boolean,
                              val callback: (dirs: ArrayList<Directory>) -> Unit) : AsyncTask<Void, Void, ArrayList<Directory>>() {
    lateinit var mConfig: Config

    override fun onPreExecute() {
        super.onPreExecute()
        mConfig = Config.newInstance(context)
    }

    override fun doInBackground(vararg params: Void): ArrayList<Directory> {
        val media = ArrayList<Medium>()
        val directories = LinkedHashMap<String, Directory>()
        val invalidFiles = ArrayList<File>()
        val showMedia = mConfig.showMedia
        for (i in 0..1) {
            if (i == 0 && (isPickVideo || showMedia == VIDEOS))
                continue

            var uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            if (i == 1) {
                if (isPickImage || showMedia == IMAGES)
                    continue

                uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
            val columns = arrayOf(MediaStore.Images.Media.DATA, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.DATE_MODIFIED,
                    MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media.SIZE)
            val order = getSortOrder()
            var cursor: Cursor? = null

            try {
                cursor = context.contentResolver.query(uri, columns, null, null, order)

                if (cursor?.moveToFirst() == true) {
                    do {
                        val fullPath = cursor.getStringValue(MediaStore.Images.Media.DATA) ?: continue
                        val file = File(fullPath)
                        val size = cursor.getLongValue(MediaStore.Images.Media.SIZE)

                        if (size == 0L) {
                            invalidFiles.add(file)
                            continue
                        }

                        val name = cursor.getStringValue(MediaStore.Images.Media.DISPLAY_NAME) ?: ""
                        val dateModified = cursor.getLongValue(MediaStore.Images.Media.DATE_MODIFIED)
                        val dateTaken = cursor.getLongValue(MediaStore.Images.Media.DATE_TAKEN)
                        media.add(Medium(name, fullPath, i == 1, dateModified, dateTaken, size))
                    } while (cursor.moveToNext())
                }
            } finally {
                cursor?.close()
            }
        }

        Medium.sorting = mConfig.fileSorting
        media.sort()

        for ((name, path, isVideo, dateModified, dateTaken, size) in media) {
            val parentDir = File(path).parent ?: continue
            if (directories.containsKey(parentDir)) {
                val directory: Directory = directories[parentDir]!!
                val newImageCnt = directory.mediaCnt + 1
                directory.mediaCnt = newImageCnt
                directory.addSize(size)
            } else {
                var dirName = context.getHumanizedFilename(parentDir)
                if (mConfig.getIsFolderHidden(parentDir)) {
                    dirName += " ${context.resources.getString(R.string.hidden)}"
                }

                directories.put(parentDir, Directory(parentDir, path, dirName, 1, dateModified, dateTaken, size))
            }
        }

        context.scanFiles(invalidFiles) {}
        val dirs = ArrayList(directories.values.filter { File(it.path).exists() })

        filterDirectories(dirs)
        Directory.sorting = mConfig.directorySorting
        dirs.sort()

        return movePinnedToFront(dirs)
    }

    private fun movePinnedToFront(dirs: ArrayList<Directory>): ArrayList<Directory> {
        val foundFolders = ArrayList<Directory>()
        val pinnedFolders = mConfig.pinnedFolders

        dirs.forEach { if (pinnedFolders.contains(it.path)) foundFolders.add(it) }
        dirs.removeAll(foundFolders)
        dirs.addAll(0, foundFolders)
        return dirs
    }

    override fun onPostExecute(dirs: ArrayList<Directory>) {
        super.onPostExecute(dirs)
        callback.invoke(dirs)
    }

    // sort the files at querying too, just to get the correct thumbnail
    private fun getSortOrder(): String {
        val sorting = mConfig.directorySorting
        var sortBy = MediaStore.Images.Media.DATE_MODIFIED
        if (sorting and SORT_BY_NAME != 0) {
            sortBy = MediaStore.Images.Media.DATA
        }

        if (sorting and SORT_DESCENDING != 0) {
            sortBy += " DESC"
        }
        return sortBy
    }

    private fun filterDirectories(dirs: MutableList<Directory>) {
        if (!mConfig.showHiddenFolders)
            removeHiddenFolders(dirs)

        removeNoMediaFolders(dirs)
    }

    private fun removeHiddenFolders(dirs: MutableList<Directory>) {
        val hiddenDirs = mConfig.hiddenFolders
        val ignoreDirs = dirs.filter { hiddenDirs.contains(it.path) }
        dirs.removeAll(ignoreDirs)
    }

    private fun removeNoMediaFolders(dirs: MutableList<Directory>) {
        val showHiddenFolders = mConfig.showHiddenFolders
        if (!showHiddenFolders) {
            val ignoreDirs = ArrayList<Directory>()
            for (d in dirs) {
                val dir = File(d.path)
                if (dir.exists() && dir.isDirectory) {
                    val res = dir.list { file, filename -> filename == ".nomedia" }
                    if (res?.isNotEmpty() == true)
                        ignoreDirs.add(d)
                }
            }

            dirs.removeAll(ignoreDirs)
        }
    }
}
