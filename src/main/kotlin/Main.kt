package org.example


import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.cloud.storage.StorageOptions
import kotlin.time.measureTimedValue
import com.google.api.services.drive.*
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials

data class File(
    val name: String,
    val dimensions: String,
)

fun getFileNames(bucketName: String): List<File> {
    val (storage, duration) = measureTimedValue { StorageOptions.getDefaultInstance().service }
    println(duration.inWholeMilliseconds)

    val (blobs, duration2) = measureTimedValue { storage.list(bucketName) }
    println(duration2.inWholeMilliseconds)


    return blobs.values.map {
        File(
            it.name,
            it.metadata?.get("dimensions") ?: "(unknown)"
        )
    }
}

fun uploadToDrive(driveService: Drive, driveId: String, file: java.io.File) {
    val fileContent = com.google.api.client.http.FileContent("application/octet-stream", file)
    val metadata = com.google.api.services.drive.model.File().apply {
        name = file.name
        parents = listOf(driveId)
    }

    val file = driveService.files().create(metadata, fileContent)
        .setFields("id, name")
        .setSupportsAllDrives(true)
        .execute()

    println("File ID: ${file.id}")
    println("File Name: ${file.name}")
}

fun getCredentials() = GoogleCredentials
    .getApplicationDefault()
    .createScoped(listOf(DriveScopes.DRIVE))

fun getDriveService(credentials: GoogleCredentials) = Drive.Builder(
    GoogleNetHttpTransport.newTrustedTransport(),
    GsonFactory.getDefaultInstance(),
    HttpCredentialsAdapter(credentials)
)
    .setApplicationName("example-app")
    .build()

fun getSharedDrives(driveService: Drive) =
    driveService.drives()
        .list()
        .setFields("drives(id, name)")
        .execute()

fun getFiles(driveService: Drive, driveId: String, q: String) =
    driveService.files().list()
        .setDriveId(driveId)
        .setIncludeItemsFromAllDrives(true)
        .setCorpora("drive")
        .setSupportsAllDrives(true)
        .setQ(q)
        .setFields("files(id, name, parents)")
        .execute()
        .files

fun moveFile(driveService: Drive, toFolderId: String, fileId: String) {
    val file = driveService.files().get(fileId)
        .setFields("parents")
        .setSupportsAllDrives(true)
        .execute()
    val formerParents = file.parents.joinToString(",")
    println(formerParents)

    // Move the file to the new folder
    driveService.files().update(fileId, null)
        .setSupportsAllDrives(true)
        .setAddParents(toFolderId)
        .setRemoveParents(formerParents)
        .execute()
}

const val FOLDER_MIME_TYPE = "mimeType = 'application/vnd.google-apps.folder'"
const val V2_DRIVE_ID = "0AO5ageEAi854Uk9PVA"

// We wouldn't hard code the following, rather we would search for it using the above functions
const val ARCHIVE_ID = "16DyU1zZSrxuJPchwC2_roOXMk9pNZAmG"

fun main() {
//    println(measureTimeMillis {
//        while(true) {
//            val fileNames = getFileNames("jezzas-test")
//            println(fileNames)
//            Thread.sleep(1000)
//        }
//    })

    val creds = getCredentials()
    val driveService = getDriveService(creds)

    println("Searching for shared drives")
    val sharedDrives = getSharedDrives(driveService)
    println("These shared drives are visible: ${sharedDrives.map { it.value}}")

    println("Look for folders in 'V2 - General' matching Jezza")
    val folders = getFiles(
        driveService = driveService,
        driveId = V2_DRIVE_ID,
        q = "$FOLDER_MIME_TYPE and name contains 'Jezza'")
    println("Matching folders are ${folders}")

    // Just take the first one for this spike
    val folderId = folders[0]["id"] as String? ?: throw Error("Couldn't find folder matching 'Jezza'")

    val file = java.io.File("/Users/jezza/Desktop/temp.txt")

    // Is there already a file in this folder with the same name?
    val existingFiles = getFiles(
        driveService = driveService,
        driveId = V2_DRIVE_ID,
        q = "'$folderId' in parents and name = '${file.name}' and trashed = false"
    )

    if (existingFiles.isNotEmpty()) {
        println(existingFiles)
        println("Found ${file.name}, moving to Archive")
        moveFile(driveService, toFolderId = ARCHIVE_ID, fileId = existingFiles[0].id)
    } else {
        println("File wasn't found, not archiving")
    }

    uploadToDrive(driveService, folderId, file)
}
