package com.example.photogallery

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.photogallery.data.AppDatabase
import com.example.photogallery.data.Collection
import com.example.photogallery.data.CollectionItem
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Vector

class SyncService(private val context: Context) {

    private val database = AppDatabase.getDatabase(context)
    private val collectionDao = database.collectionDao()
    private val changeLogDao = database.changeLogDao()

    suspend fun testConnection(config: SyncConfig): Boolean = withContext(Dispatchers.IO) {
        var session: Session? = null
        var channel: ChannelSftp? = null
        
        try {
            val jsch = JSch()
            session = jsch.getSession(config.username, config.vpsAddress, config.port)
            session.setPassword(config.password)
            session.setConfig("StrictHostKeyChecking", "no")
            session.connect(10000) // 10 seconds timeout

            channel = session.openChannel("sftp") as ChannelSftp
            channel.connect()

            // Try to access the remote directory
            try {
                channel.ls(config.remotePath)
            } catch (e: Exception) {
                // Directory doesn't exist, try to create it
                createRemoteDirectory(channel, config.remotePath)
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            channel?.disconnect()
            session?.disconnect()
        }
    }

    suspend fun uploadCollections(config: SyncConfig): Boolean = withContext(Dispatchers.IO) {
        var session: Session? = null
        var channel: ChannelSftp? = null
        var uploadedFiles = 0
        var totalFiles = 0
        
        try {
            // Get all collections and their items from database
            val collections = collectionDao.getAllCollectionsSync()

            // Connect to SFTP
            val jsch = JSch()
            session = jsch.getSession(config.username, config.vpsAddress, config.port)
            session.setPassword(config.password)
            session.setConfig("StrictHostKeyChecking", "no")
            session.connect()

            channel = session.openChannel("sftp") as ChannelSftp
            channel.connect()

            // Create remote root directory if it doesn't exist
            try {
                channel.ls(config.remotePath)
            } catch (e: Exception) {
                createRemoteDirectory(channel, config.remotePath)
            }

            // First, get existing remote collections for comparison
            val existingRemoteCollections = getRemoteCollections(channel, config.remotePath)
            val localCollectionNames = collections.map { sanitizeFileName(it.name) }.toSet()
            
            // Upload each collection as a folder
            for (collection in collections) {
                val collectionPath = "${config.remotePath}/${sanitizeFileName(collection.name)}"
                
                // Create collection directory
                try {
                    channel.mkdir(collectionPath)
                } catch (e: Exception) {
                    // Directory might already exist, that's ok
                }

                // Get items for this collection
                val items = collectionDao.getItemsForCollectionSync(collection.id)
                totalFiles += items.size
                val localFileNames = items.mapNotNull { item ->
                    val file = File(item.mediaPath)
                    if (file.exists()) file.name else null
                }.toSet()

                // Get existing files in this remote collection
                val existingRemoteFiles = getRemoteFilesInCollection(channel, collectionPath)
                
                // Upload each image/video file
                for (item in items) {
                    try {
                        val sourceFile = File(item.mediaPath)
                        if (sourceFile.exists() && sourceFile.canRead()) {
                            val fileName = sourceFile.name
                            val remotePath = "$collectionPath/$fileName"
                            
                            // Check if file already exists and has same size (skip if identical)
                            var shouldUpload = true
                            try {
                                val remoteAttrs = channel.stat(remotePath)
                                if (remoteAttrs.size == sourceFile.length()) {
                                    shouldUpload = false // File exists and same size
                                }
                            } catch (e: Exception) {
                                // File doesn't exist, need to upload
                            }

                            if (shouldUpload) {
                                val inputStream = FileInputStream(sourceFile)
                                channel.put(inputStream, remotePath)
                                inputStream.close()
                                uploadedFiles++
                            }
                        }
                    } catch (e: Exception) {
                        // Log error but continue with other files
                        e.printStackTrace()
                    }
                }
                
                // Delete files that exist remotely but not locally (mirror sync)
                for (remoteFileName in existingRemoteFiles) {
                    if (!localFileNames.contains(remoteFileName)) {
                        try {
                            channel.rm("$collectionPath/$remoteFileName")
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
            
            // Delete remote collections that don't exist locally (mirror sync)
            for (remoteCollectionName in existingRemoteCollections) {
                if (!localCollectionNames.contains(remoteCollectionName)) {
                    try {
                        deleteRemoteDirectory(channel, "${config.remotePath}/$remoteCollectionName")
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            // Record upload change log
            changeLogDao.insertChangeLog(
                com.example.photogallery.data.ChangeLog(
                    timestamp = System.currentTimeMillis(),
                    action = "MIRROR_SYNC",
                    description = "Mirror synced ${collections.size} collections ($uploadedFiles/$totalFiles files) to VPS. Remote files/folders deleted when removed locally."
                )
            )

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            channel?.disconnect()
            session?.disconnect()
        }
    }

    suspend fun downloadCollections(config: SyncConfig): Boolean = withContext(Dispatchers.IO) {
        var session: Session? = null
        var channel: ChannelSftp? = null
        var downloadedFiles = 0
        var totalCollections = 0
        
        try {
            // Connect to SFTP
            val jsch = JSch()
            session = jsch.getSession(config.username, config.vpsAddress, config.port)
            session.setPassword(config.password)
            session.setConfig("StrictHostKeyChecking", "no")
            session.connect()

            channel = session.openChannel("sftp") as ChannelSftp
            channel.connect()

            // Create local download directory in Pictures folder
            val downloadDir = getPublicPicturesDir()
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }

            // Get existing local collections for comparison
            val existingLocalCollections = collectionDao.getAllCollectionsSync()
            val existingLocalCollectionMap = existingLocalCollections.associateBy { it.name }

            // List all directories in the remote path (each represents a collection)
            val remoteDirs = (channel.ls(config.remotePath) as Vector<ChannelSftp.LsEntry>).filter { 
                it.attrs.isDir && !it.filename.startsWith(".")
            }
            val remoteCollectionNames = remoteDirs.map { it.filename }.toSet()

            // Delete local collections that don't exist on VPS (mirror sync)
            for (localCollection in existingLocalCollections) {
                if (!remoteCollectionNames.contains(localCollection.name)) {
                    // Delete all items in this collection first
                    val items = collectionDao.getItemsForCollectionSync(localCollection.id)
                    for (item in items) {
                        try {
                            val file = File(item.mediaPath)
                            if (file.exists()) {
                                file.delete()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    // Delete the collection from database
                    collectionDao.deleteCollection(localCollection)
                    
                    // Delete local directory if it exists
                    try {
                        val localCollectionDir = File(downloadDir, localCollection.name)
                        if (localCollectionDir.exists()) {
                            localCollectionDir.deleteRecursively()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            android.util.Log.d("SyncService", "开始下载，远程收藏数: ${remoteDirs.size}")
            
            for (remoteDir in remoteDirs) {
                totalCollections++
                val collectionName = remoteDir.filename
                val remoteDirPath = "${config.remotePath}/$collectionName"
                
                android.util.Log.d("SyncService", "处理收藏: $collectionName")
                
                // Get or create collection in database
                val existingCollection = existingLocalCollectionMap[collectionName]
                val collectionId = if (existingCollection != null) {
                    android.util.Log.d("SyncService", "使用现有收藏: $collectionName, ID: ${existingCollection.id}")
                    existingCollection.id
                } else {
                    val collection = Collection(id = 0, name = collectionName)
                    val newId = collectionDao.insertCollection(collection)
                    android.util.Log.d("SyncService", "创建新收藏: $collectionName, ID: $newId")
                    newId
                }
                
                // Create local directory for this collection
                val localCollectionDir = File(downloadDir, collectionName)
                if (!localCollectionDir.exists()) {
                    localCollectionDir.mkdirs()
                }

                // Get existing local files in this collection
                val existingLocalItems = collectionDao.getItemsForCollectionSync(collectionId)
                val existingLocalFileNames = existingLocalItems.map { File(it.mediaPath).name }.toSet()

                // List and download all files in this collection directory
                try {
                    val remoteFiles = (channel.ls(remoteDirPath) as Vector<ChannelSftp.LsEntry>).filter { 
                        !it.attrs.isDir && isImageOrVideo(it.filename)
                    }
                    val remoteFileNames = remoteFiles.map { it.filename }.toSet()
                    
                    android.util.Log.d("SyncService", "收藏 $collectionName 中有 ${remoteFiles.size} 个文件")

                    // Delete local files that don't exist on VPS (mirror sync)
                    for (localItem in existingLocalItems) {
                        val localFileName = File(localItem.mediaPath).name
                        if (!remoteFileNames.contains(localFileName)) {
                            try {
                                // Delete the file from filesystem
                                val file = File(localItem.mediaPath)
                                if (file.exists()) {
                                    file.delete()
                                }
                                // Remove from database
                                collectionDao.removeItemFromCollection(localItem.collectionId, localItem.mediaPath)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }

                    var collectionDownloadedFiles = 0
                    for (remoteFile in remoteFiles) {
                        try {
                            val fileName = remoteFile.filename
                            val localFilePath = File(localCollectionDir, fileName)
                            val remoteFilePath = "$remoteDirPath/$fileName"

                            // Download file if it doesn't exist locally or sizes differ
                            var shouldDownload = true
                            if (localFilePath.exists()) {
                                if (localFilePath.length() == remoteFile.attrs.size) {
                                    shouldDownload = false
                                    android.util.Log.d("SyncService", "文件已存在，跳过: $fileName")
                                } else {
                                    android.util.Log.d("SyncService", "文件大小不匹配，重新下载: $fileName")
                                }
                            } else {
                                android.util.Log.d("SyncService", "准备下载新文件: $fileName")
                            }

                            if (shouldDownload) {
                                android.util.Log.d("SyncService", "开始下载文件: $fileName")
                                var actualFilePath = localFilePath.absolutePath
                                var downloadSuccess = false
                                
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    // Android 10+ 使用MediaStore API下载文件
                                    val downloadedPath = downloadFileWithMediaStore(channel, remoteFilePath, fileName, collectionName)
                                    if (downloadedPath != null) {
                                        actualFilePath = downloadedPath
                                        downloadedFiles++
                                        collectionDownloadedFiles++
                                        downloadSuccess = true
                                        android.util.Log.d("SyncService", "MediaStore下载成功: $fileName -> $downloadedPath")
                                    } else {
                                        // MediaStore失败，回退到传统方式
                                        android.util.Log.w("SyncService", "MediaStore下载失败，回退到传统方式: $fileName")
                                        try {
                                            val outputStream = FileOutputStream(localFilePath)
                                            channel.get(remoteFilePath, outputStream)
                                            outputStream.close()
                                            
                                            // 设置文件权限为可读写
                                            try {
                                                localFilePath.setReadable(true, false)
                                                localFilePath.setWritable(true, false)
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                            
                                            // 通知媒体扫描器扫描新文件
                                            scanMediaFile(localFilePath.absolutePath)
                                            downloadedFiles++
                                            collectionDownloadedFiles++
                                            downloadSuccess = true
                                            android.util.Log.d("SyncService", "回退传统方式下载成功: $fileName")
                                        } catch (e: Exception) {
                                            android.util.Log.e("SyncService", "回退传统方式下载也失败: $fileName", e)
                                        }
                                    }
                                } else {
                                    // Android 9及以下使用传统方式
                                    try {
                                        val outputStream = FileOutputStream(localFilePath)
                                        channel.get(remoteFilePath, outputStream)
                                        outputStream.close()
                                        
                                        // 设置文件权限为可读写
                                        try {
                                            localFilePath.setReadable(true, false)
                                            localFilePath.setWritable(true, false)
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                        
                                        // 通知媒体扫描器扫描新文件
                                        scanMediaFile(localFilePath.absolutePath)
                                        downloadedFiles++
                                        collectionDownloadedFiles++
                                        downloadSuccess = true
                                        android.util.Log.d("SyncService", "传统方式下载成功: $fileName")
                                    } catch (e: Exception) {
                                        android.util.Log.e("SyncService", "传统方式下载失败: $fileName", e)
                                    }
                                }
                                
                                // Add file to collection in database if download was successful
                                if (downloadSuccess && !existingLocalFileNames.contains(fileName)) {
                                    try {
                                        val collectionItem = CollectionItem(
                                            collectionId = collectionId,
                                            mediaPath = actualFilePath
                                        )
                                        collectionDao.insertItemIntoCollection(collectionItem)
                                        android.util.Log.d("SyncService", "数据库记录添加成功: $fileName")
                                    } catch (e: Exception) {
                                        android.util.Log.e("SyncService", "数据库记录添加失败: $fileName", e)
                                    }
                                }
                            } else {
                                // 即使没有下载，也要确保数据库中有记录（如果文件存在）
                                if (!existingLocalFileNames.contains(fileName) && localFilePath.exists()) {
                                    try {
                                        val collectionItem = CollectionItem(
                                            collectionId = collectionId,
                                            mediaPath = localFilePath.absolutePath
                                        )
                                        collectionDao.insertItemIntoCollection(collectionItem)
                                        android.util.Log.d("SyncService", "跳过下载但添加数据库记录: $fileName")
                                    } catch (e: Exception) {
                                        android.util.Log.e("SyncService", "跳过下载时数据库记录添加失败: $fileName", e)
                                    }
                                }
                            }

                        } catch (e: Exception) {
                            android.util.Log.e("SyncService", "处理文件时出错: ${remoteFile.filename}", e)
                            e.printStackTrace()
                        }
                    }
                    
                    android.util.Log.d("SyncService", "收藏 $collectionName 完成，下载了 $collectionDownloadedFiles 个文件")
                } catch (e: Exception) {
                    android.util.Log.e("SyncService", "处理收藏 $collectionName 时出错", e)
                    e.printStackTrace()
                }
            }

            // Record download change log
            changeLogDao.insertChangeLog(
                com.example.photogallery.data.ChangeLog(
                    timestamp = System.currentTimeMillis(),
                    action = "MIRROR_SYNC",
                    description = "Mirror synced $totalCollections collections ($downloadedFiles files) from VPS. Local files/folders deleted when removed from VPS."
                )
            )
            
            // 扫描整个下载目录，确保所有文件都被媒体库识别
            scanDownloadDirectory()

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            channel?.disconnect()
            session?.disconnect()
        }
    }

    private fun createRemoteDirectory(channel: ChannelSftp, path: String) {
        val parts = path.split("/")
        var currentPath = ""
        
        for (part in parts) {
            if (part.isEmpty()) continue
            currentPath += "/$part"
            try {
                channel.ls(currentPath)
            } catch (e: Exception) {
                // Directory doesn't exist, create it
                try {
                    channel.mkdir(currentPath)
                } catch (createException: Exception) {
                    // Ignore if directory already exists
                }
            }
        }
    }

    private fun sanitizeFileName(name: String): String {
        // Replace invalid characters for file/folder names
        return name.replace(Regex("[<>:\"/\\\\|?*]"), "_")
            .replace(" ", "_")
            .trim()
    }

    private fun isImageOrVideo(fileName: String): Boolean {
        val lowercaseFileName = fileName.lowercase()
        val imageExtensions = listOf(".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp")
        val videoExtensions = listOf(".mp4", ".avi", ".mkv", ".mov", ".wmv", ".flv", ".webm", ".m4v")
        
        return imageExtensions.any { lowercaseFileName.endsWith(it) } ||
               videoExtensions.any { lowercaseFileName.endsWith(it) }
    }

    // Get local latest change information
    suspend fun getLocalLatestChange(): com.example.photogallery.data.ChangeLog? {
        return changeLogDao.getLatestChange()
    }

    // Get VPS latest change information
    suspend fun getVpsLatestChange(config: SyncConfig): VpsChangeInfo? = withContext(Dispatchers.IO) {
        var session: Session? = null
        var channel: ChannelSftp? = null
        
        try {
            val jsch = JSch()
            session = jsch.getSession(config.username, config.vpsAddress, config.port)
            session.setPassword(config.password)
            session.setConfig("StrictHostKeyChecking", "no")
            session.connect(10000)

            channel = session.openChannel("sftp") as ChannelSftp
            channel.connect()

            // Check directories in remote path (each represents a collection)
            try {
                val remoteDirs = (channel.ls(config.remotePath) as Vector<ChannelSftp.LsEntry>).filter { 
                    it.attrs.isDir && !it.filename.startsWith(".")
                }
                
                if (remoteDirs.isEmpty()) {
                    VpsChangeInfo(
                        lastModified = 0L,
                        collectionsCount = 0,
                        description = "No collections found on VPS"
                    )
                } else {
                    // Find the most recently modified directory
                    val latestDir = remoteDirs.maxByOrNull { it.attrs.mTime }
                    val modTime = (latestDir?.attrs?.mTime ?: 0) * 1000L
                    
                    VpsChangeInfo(
                        lastModified = modTime,
                        collectionsCount = remoteDirs.size,
                        description = "VPS has ${remoteDirs.size} collections"
                    )
                }
            } catch (e: Exception) {
                // Directory doesn't exist
                VpsChangeInfo(
                    lastModified = 0L,
                    collectionsCount = 0,
                    description = "Remote directory not found"
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            channel?.disconnect()
            session?.disconnect()
        }
    }

    suspend fun compareCollections(config: SyncConfig): ComparisonResult? = withContext(Dispatchers.IO) {
        var session: Session? = null
        var channel: ChannelSftp? = null
        
        try {
            // Get local collections
            val localCollections = collectionDao.getAllCollectionsSync()
            val localCollectionMap = mutableMapOf<String, Collection>()
            val localItemsMap = mutableMapOf<String, List<String>>() // collection name -> file names
            
            for (collection in localCollections) {
                localCollectionMap[collection.name] = collection
                val items = collectionDao.getItemsForCollectionSync(collection.id)
                localItemsMap[collection.name] = items.map { File(it.mediaPath).name }
            }

            // Connect to VPS and get remote collections
            val jsch = JSch()
            session = jsch.getSession(config.username, config.vpsAddress, config.port)
            session.setPassword(config.password)
            session.setConfig("StrictHostKeyChecking", "no")
            session.connect(10000)

            channel = session.openChannel("sftp") as ChannelSftp
            channel.connect()

            val remoteCollectionMap = mutableMapOf<String, List<String>>() // collection name -> file names
            
            try {
                val remoteDirs = (channel.ls(config.remotePath) as Vector<ChannelSftp.LsEntry>).filter { 
                    it.attrs.isDir && !it.filename.startsWith(".")
                }
                
                for (remoteDir in remoteDirs) {
                    val collectionName = remoteDir.filename
                    val remoteDirPath = "${config.remotePath}/$collectionName"
                    
                    try {
                        val remoteFiles = (channel.ls(remoteDirPath) as Vector<ChannelSftp.LsEntry>).filter { 
                            !it.attrs.isDir && isImageOrVideo(it.filename)
                        }
                        
                        remoteCollectionMap[collectionName] = remoteFiles.map { it.filename }
                    } catch (e: Exception) {
                        remoteCollectionMap[collectionName] = emptyList()
                    }
                }
            } catch (e: Exception) {
                // No remote directory or error accessing it
            }

            // Compare collections
            val onlyLocal = mutableListOf<String>()
            val onlyRemote = mutableListOf<String>()
            val different = mutableListOf<CollectionDifference>()
            val identical = mutableListOf<String>()

            // Check for collections only in local
            for (localName in localItemsMap.keys) {
                if (!remoteCollectionMap.containsKey(localName)) {
                    onlyLocal.add(localName)
                }
            }

            // Check for collections only in remote
            for (remoteName in remoteCollectionMap.keys) {
                if (!localItemsMap.containsKey(remoteName)) {
                    onlyRemote.add(remoteName)
                }
            }

            // Check for differences in common collections
            for (commonName in localItemsMap.keys.intersect(remoteCollectionMap.keys)) {
                val localFiles = localItemsMap[commonName]!!.toSet()
                val remoteFiles = remoteCollectionMap[commonName]!!.toSet()

                if (localFiles == remoteFiles) {
                    identical.add(commonName)
                } else {
                    val onlyInLocal = localFiles - remoteFiles
                    val onlyInRemote = remoteFiles - localFiles
                    different.add(
                        CollectionDifference(
                            name = commonName,
                            onlyInLocal = onlyInLocal.toList(),
                            onlyInRemote = onlyInRemote.toList()
                        )
                    )
                }
            }

            ComparisonResult(
                onlyLocal = onlyLocal,
                onlyRemote = onlyRemote,
                different = different,
                identical = identical,
                remoteCollectionFiles = remoteCollectionMap
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            channel?.disconnect()
            session?.disconnect()
        }
    }

    private fun getRemoteCollections(channel: ChannelSftp, remotePath: String): Set<String> {
        return try {
            val entries = channel.ls(remotePath) as Vector<ChannelSftp.LsEntry>
            entries.filter { entry ->
                entry.attrs.isDir && entry.filename != "." && entry.filename != ".."
            }.map { it.filename }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    private fun getRemoteFilesInCollection(channel: ChannelSftp, collectionPath: String): Set<String> {
        return try {
            val entries = channel.ls(collectionPath) as Vector<ChannelSftp.LsEntry>
            entries.filter { entry ->
                !entry.attrs.isDir && entry.filename != "." && entry.filename != ".."
            }.map { it.filename }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    private fun deleteRemoteDirectory(channel: ChannelSftp, dirPath: String) {
        try {
            // First, delete all files in the directory
            val entries = channel.ls(dirPath) as Vector<ChannelSftp.LsEntry>
            for (entry in entries) {
                if (entry.filename != "." && entry.filename != "..") {
                    val entryPath = "$dirPath/${entry.filename}"
                    if (entry.attrs.isDir) {
                        // Recursively delete subdirectory
                        deleteRemoteDirectory(channel, entryPath)
                    } else {
                        // Delete file
                        channel.rm(entryPath)
                    }
                }
            }
            // Then delete the empty directory
            channel.rmdir(dirPath)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun downloadFileWithMediaStore(channel: ChannelSftp, remoteFilePath: String, fileName: String, collectionName: String): String? {
        try {
            android.util.Log.d("SyncService", "MediaStore下载开始: $fileName")
            
            val isImage = fileName.lowercase().run {
                endsWith(".jpg") || endsWith(".jpeg") || endsWith(".png") || 
                endsWith(".gif") || endsWith(".bmp") || endsWith(".webp")
            }
            
            val contentUri = if (isImage) {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
            
            // 首先检查文件是否已存在，如果存在则删除
            val expectedPath = "/storage/emulated/0/Pictures/PhotoGallery/$collectionName/$fileName"
            deleteExistingMediaStoreEntry(expectedPath, contentUri)
            
            val contentValues = android.content.ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/PhotoGallery/$collectionName")
                if (isImage) {
                    put(MediaStore.Images.Media.MIME_TYPE, getMimeType(fileName))
                } else {
                    put(MediaStore.Video.Media.MIME_TYPE, getMimeType(fileName))
                }
            }
            
            android.util.Log.d("SyncService", "创建MediaStore条目: $fileName")
            val uri = context.contentResolver.insert(contentUri, contentValues)
            if (uri != null) {
                android.util.Log.d("SyncService", "MediaStore URI创建成功: $uri")
                
                var downloadBytes = 0L
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    android.util.Log.d("SyncService", "开始传输文件: $remoteFilePath")
                    
                    // 使用缓冲区读取和写入
                    val inputStream = channel.get(remoteFilePath)
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        downloadBytes += bytesRead
                    }
                    inputStream.close()
                    outputStream.flush()
                    
                    android.util.Log.d("SyncService", "文件传输完成: $fileName, 大小: $downloadBytes 字节")
                }
                
                // 获取实际的文件路径
                val projection = arrayOf(MediaStore.MediaColumns.DATA)
                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                        val filePath = cursor.getString(dataIndex)
                        android.util.Log.d("SyncService", "MediaStore文件路径: $filePath")
                        return filePath
                    }
                }
            } else {
                android.util.Log.e("SyncService", "MediaStore URI创建失败: $fileName")
            }
        } catch (e: Exception) {
            android.util.Log.e("SyncService", "MediaStore下载异常: $fileName", e)
            e.printStackTrace()
        }
        return null
    }

    private fun deleteExistingMediaStoreEntry(filePath: String, contentUri: android.net.Uri) {
        try {
            val projection = arrayOf(MediaStore.MediaColumns._ID)
            val selection = "${MediaStore.MediaColumns.DATA} = ?"
            val selectionArgs = arrayOf(filePath)
            
            context.contentResolver.query(contentUri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    val deleteUri = android.content.ContentUris.withAppendedId(contentUri, id)
                    val deletedRows = context.contentResolver.delete(deleteUri, null, null)
                    android.util.Log.d("SyncService", "删除现有MediaStore条目: $filePath, 删除行数: $deletedRows")
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("SyncService", "删除现有MediaStore条目失败: $filePath", e)
        }
    }

    private fun getMimeType(fileName: String): String {
        return when (fileName.lowercase().substringAfterLast('.')) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "webp" -> "image/webp"
            "mp4" -> "video/mp4"
            "avi" -> "video/x-msvideo"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "wmv" -> "video/x-ms-wmv"
            "flv" -> "video/x-flv"
            "webm" -> "video/webm"
            "m4v" -> "video/x-m4v"
            else -> "application/octet-stream"
        }
    }

    private fun scanDownloadDirectory() {
        try {
            val downloadDir = getPublicPicturesDir()
            if (downloadDir.exists()) {
                val allFiles = mutableListOf<String>()
                downloadDir.walkTopDown().forEach { file ->
                    if (file.isFile && isImageOrVideo(file.name)) {
                        allFiles.add(file.absolutePath)
                    }
                }
                
                if (allFiles.isNotEmpty()) {
                    // 批量扫描所有文件
                    android.media.MediaScannerConnection.scanFile(
                        context,
                        allFiles.toTypedArray(),
                        null
                    ) { path, uri ->
                        // 扫描完成回调
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getPublicPicturesDir(): File {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 使用标准的Pictures目录
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "PhotoGallery")
        } else {
            // Android 9及以下版本
            File(Environment.getExternalStorageDirectory(), "Pictures/PhotoGallery")
        }
    }

    private fun scanMediaFile(filePath: String) {
        try {
            // 通知媒体扫描器扫描新文件，让系统相册能够识别
            val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            intent.data = Uri.fromFile(File(filePath))
            context.sendBroadcast(intent)
            
            // 额外使用MediaScannerConnection来确保文件被扫描
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(filePath),
                    null
                ) { path, uri ->
                    // 扫描完成回调
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

data class VpsChangeInfo(
    val lastModified: Long,
    val collectionsCount: Int,
    val description: String
)


data class CollectionDifference(
    val name: String,
    val onlyInLocal: List<String>,
    val onlyInRemote: List<String>
)

data class ComparisonResult(
    val onlyLocal: List<String>,
    val onlyRemote: List<String>, 
    val different: List<CollectionDifference>,
    val identical: List<String>,
    val remoteCollectionFiles: Map<String, List<String>> = emptyMap() // 添加远程收藏文件映射
)