package org.sunbird.job.util

import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import org.sunbird.cloud.storage.BaseStorageService
import org.sunbird.cloud.storage.factory.{StorageConfig, StorageServiceFactory}
import org.sunbird.job.BaseJobConfig
import java.io.File

class CloudStorageUtil(config: BaseJobConfig) extends Serializable {

  private val logger = LoggerFactory.getLogger(classOf[CloudStorageUtil])
  val cloudStorageType: String = config.getString("cloud_storage_type", "azure")
  var storageService: BaseStorageService = null
  val container: String = getContainerName

  @throws[Exception]
  def getService: BaseStorageService = {
    if (null == storageService) {
      val storageKey = config.getString("cloud_storage_key", "")
      val storageSecret = config.getString("cloud_storage_secret", "")
      val endPoint = config.getString("cloud_storage_endpoint", "")
      val storageEndPoint = if(StringUtils.isNotBlank(endPoint)) Option(endPoint) else None
      storageService = StorageServiceFactory.getStorageService(StorageConfig(cloudStorageType, storageKey, storageSecret, storageEndPoint))
    }
    storageService
  }

  def getContainerName: String = {
    val containerName = config.getString("cloud_storage_container", "")
    if(StringUtils.isBlank(containerName))
      throw new Exception("Container name not configured.")
    
    // Validate container name format for cloud storage
    validateContainerName(containerName)
    containerName
  }
  
  private def validateContainerName(containerName: String): Unit = {
    if (StringUtils.isBlank(containerName)) {
      throw new Exception("Container name cannot be empty or blank.")
    }
    
    // Basic validation for container name
    if (containerName.length < 3 || containerName.length > 63) {
      throw new Exception(s"Container name '$containerName' must be between 3 and 63 characters long.")
    }
    
    // Check for valid characters (alphanumeric and hyphens, no spaces)
    if (!containerName.matches("^[a-z0-9][a-z0-9-]*[a-z0-9]$")) {
      throw new Exception(s"Container name '$containerName' contains invalid characters. Only lowercase letters, numbers, and hyphens are allowed.")
    }
    
    // Cannot start or end with hyphen
    if (containerName.startsWith("-") || containerName.endsWith("-")) {
      throw new Exception(s"Container name '$containerName' cannot start or end with a hyphen.")
    }
  }

  def uploadFile(folderName: String, file: File, slug: Option[Boolean] = Option(true), container: String = container): Array[String] = {
    try {
      val slugFile = if (slug.getOrElse(true)) Slug.createSlugFile(file) else file
      val objectKey = folderName + "/" + slugFile.getName
      val url = getService.upload(container, slugFile.getAbsolutePath, objectKey, Option.apply(false), Option.apply(1), Option.apply(5), Option.empty)
      Array[String](objectKey, url)
    } catch {
      case ex: Exception =>
        val errorMsg = s"Failed to upload file '${file.getName}' to container '$container' in folder '$folderName'"
        logger.error(errorMsg, ex)
        
        // Check for specific cloud storage errors and provide better error messages
        ex.getMessage match {
          case msg if msg.contains("400 Bad Request") =>
            throw new Exception(s"$errorMsg. Container '$container' may not exist or may have invalid name format. Please verify container configuration.", ex)
          case msg if msg.contains("403 Forbidden") =>
            throw new Exception(s"$errorMsg. Access denied - please check cloud storage credentials and permissions.", ex)
          case msg if msg.contains("404 Not Found") =>
            throw new Exception(s"$errorMsg. Container '$container' not found. Please verify container exists.", ex)
          case _ =>
            throw new Exception(errorMsg, ex)
        }
    }
  }

  def copyObjectsByPrefix(sourcePrefix: String, destinationPrefix: String, isFolder: Boolean): Unit = {
    getService.copyObjects(container, sourcePrefix, container, destinationPrefix, Option.apply(isFolder))
  }

  def getURI(prefix: String, isDirectory: Option[Boolean]): String = {
    getService.getUri(getContainerName, prefix, isDirectory)
  }

  def uploadDirectory(folderName: String, directory: File, slug: Option[Boolean] = Option(true)): Array[String] = {
    val slugFile = if (slug.getOrElse(true)) Slug.createSlugFile(directory) else directory
    val objectKey = folderName + File.separator
    val url = getService.upload(getContainerName, slugFile.getAbsolutePath, objectKey, Option.apply(true), Option.apply(1), Option.apply(5), Option.empty)
    Array[String](objectKey, url)
  }

  def deleteFile(key: String, isDirectory: Option[Boolean] = Option(false)): Unit = {
    getService.deleteObject(getContainerName, key, isDirectory)
  }

  def downloadFile(downloadPath: String, file: String, slug: Option[Boolean] = Option(false)): Unit = {
    getService.download(getContainerName, file, downloadPath, slug)
  }

}
