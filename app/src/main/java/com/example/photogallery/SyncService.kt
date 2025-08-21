package com.example.photogallery

import android.content.Context
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
            }

            // Record upload change log
            changeLogDao.insertChangeLog(
                com.example.photogallery.data.ChangeLog(
                    timestamp = System.currentTimeMillis(),
                    action = "UPLOAD",
                    description = "Uploaded ${collections.size} collections ($uploadedFiles/$totalFiles files) to VPS"
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

            // Create local download directory
            val downloadDir = File(context.getExternalFilesDir(null), "downloaded_collections")
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }

            // List all directories in the remote path (each represents a collection)
            val remoteDirs = (channel.ls(config.remotePath) as Vector<ChannelSftp.LsEntry>).filter { 
                it.attrs.isDir && !it.filename.startsWith(".")
            }

            // Clear existing collections (optional - you might want to merge instead)
            collectionDao.deleteAllCollections()

            for (remoteDir in remoteDirs) {
                totalCollections++
                val collectionName = remoteDir.filename
                val remoteDirPath = "${config.remotePath}/$collectionName"
                
                // Create collection in database
                val collection = Collection(id = 0, name = collectionName)
                val collectionId = collectionDao.insertCollection(collection)
                
                // Create local directory for this collection
                val localCollectionDir = File(downloadDir, collectionName)
                if (!localCollectionDir.exists()) {
                    localCollectionDir.mkdirs()
                }

                // List and download all files in this collection directory
                try {
                    val remoteFiles = (channel.ls(remoteDirPath) as Vector<ChannelSftp.LsEntry>).filter { 
                        !it.attrs.isDir && isImageOrVideo(it.filename)
                    }

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
                                }
                            }

                            if (shouldDownload) {
                                val outputStream = FileOutputStream(localFilePath)
                                channel.get(remoteFilePath, outputStream)
                                outputStream.close()
                                downloadedFiles++
                            }

                            // Add file to collection in database
                            val collectionItem = CollectionItem(
                                collectionId = collectionId,
                                mediaPath = localFilePath.absolutePath
                            )
                            collectionDao.insertItemIntoCollection(collectionItem)

                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Record download change log
            changeLogDao.insertChangeLog(
                com.example.photogallery.data.ChangeLog(
                    timestamp = System.currentTimeMillis(),
                    action = "DOWNLOAD",
                    description = "Downloaded $totalCollections collections ($downloadedFiles files) from VPS"
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
                identical = identical
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            channel?.disconnect()
            session?.disconnect()
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
    val identical: List<String>
)