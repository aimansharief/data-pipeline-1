package org.sunbird.job.util

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{FlatSpec, Matchers}
import org.sunbird.job.BaseJobConfig

class CloudStorageUtilSpec extends FlatSpec with Matchers {

  "CloudStorageUtil" should "validate container names correctly" in {
    
    // Test with valid container name
    val validConfig = ConfigFactory.parseString(
      """
        |cloud_storage_container = "valid-container-name"
        |cloud_storage_type = "azure"
        |""".stripMargin)
    val validJobConfig = new BaseJobConfig(validConfig, "test")
    
    noException should be thrownBy {
      new CloudStorageUtil(validJobConfig)
    }
  }

  it should "reject container names that are too short" in {
    val shortConfig = ConfigFactory.parseString(
      """
        |cloud_storage_container = "ab"
        |cloud_storage_type = "azure"
        |""".stripMargin)
    val shortJobConfig = new BaseJobConfig(shortConfig, "test")
    
    val thrown = intercept[Exception] {
      new CloudStorageUtil(shortJobConfig)
    }
    thrown.getMessage should include ("must be between 3 and 63 characters long")
  }

  it should "reject container names that are too long" in {
    val longName = "a" * 64
    val longConfig = ConfigFactory.parseString(
      s"""
        |cloud_storage_container = "$longName"
        |cloud_storage_type = "azure"
        |""".stripMargin)
    val longJobConfig = new BaseJobConfig(longConfig, "test")
    
    val thrown = intercept[Exception] {
      new CloudStorageUtil(longJobConfig)
    }
    thrown.getMessage should include ("must be between 3 and 63 characters long")
  }

  it should "reject container names with invalid characters" in {
    val invalidConfig = ConfigFactory.parseString(
      """
        |cloud_storage_container = "Invalid_Container_Name"
        |cloud_storage_type = "azure"
        |""".stripMargin)
    val invalidJobConfig = new BaseJobConfig(invalidConfig, "test")
    
    val thrown = intercept[Exception] {
      new CloudStorageUtil(invalidJobConfig)
    }
    thrown.getMessage should include ("contains invalid characters")
  }

  it should "reject container names starting with hyphen" in {
    val hyphenConfig = ConfigFactory.parseString(
      """
        |cloud_storage_container = "-invalid-name"
        |cloud_storage_type = "azure"
        |""".stripMargin)
    val hyphenJobConfig = new BaseJobConfig(hyphenConfig, "test")
    
    val thrown = intercept[Exception] {
      new CloudStorageUtil(hyphenJobConfig)
    }
    thrown.getMessage should include ("cannot start or end with a hyphen")
  }

  it should "reject container names ending with hyphen" in {
    val hyphenConfig = ConfigFactory.parseString(
      """
        |cloud_storage_container = "invalid-name-"
        |cloud_storage_type = "azure"
        |""".stripMargin)
    val hyphenJobConfig = new BaseJobConfig(hyphenConfig, "test")
    
    val thrown = intercept[Exception] {
      new CloudStorageUtil(hyphenJobConfig)
    }
    thrown.getMessage should include ("cannot start or end with a hyphen")
  }

  it should "reject empty or blank container names" in {
    val emptyConfig = ConfigFactory.parseString(
      """
        |cloud_storage_container = ""
        |cloud_storage_type = "azure"
        |""".stripMargin)
    val emptyJobConfig = new BaseJobConfig(emptyConfig, "test")
    
    val thrown = intercept[Exception] {
      new CloudStorageUtil(emptyJobConfig)
    }
    thrown.getMessage should include ("Container name not configured")
  }
}