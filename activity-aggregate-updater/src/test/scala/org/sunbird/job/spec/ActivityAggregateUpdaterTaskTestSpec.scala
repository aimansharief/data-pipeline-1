package org.sunbird.job.spec

import java.util

import com.google.gson.Gson
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction.SourceContext
import org.cassandraunit.CQLDataLoader
import org.cassandraunit.dataset.cql.FileCQLDataSet
import org.cassandraunit.utils.EmbeddedCassandraServerHelper
import org.mockito.Mockito._
import org.sunbird.job.cache.RedisConnect
import org.sunbird.job.fixture.EventFixture
import org.sunbird.job.task.ActivityAggregateUpdaterStreamTask
import org.sunbird.job.util.CassandraUtil
import org.sunbird.spec.BaseMetricsReporter


class ActivityAggregateUpdaterTaskTestSpec extends BaseActivityAggregateTestSpec {

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val redisConnect = new RedisConnect(courseAggregatorConfig)
    jedis = redisConnect.getConnection(courseAggregatorConfig.nodeStore)
    EmbeddedCassandraServerHelper.startEmbeddedCassandra(80000L)
    cassandraUtil = new CassandraUtil(courseAggregatorConfig.dbHost, courseAggregatorConfig.dbPort)
    val session = cassandraUtil.session

    val dataLoader = new CQLDataLoader(session)
    dataLoader.load(new FileCQLDataSet(getClass.getResource("/test.cql").getPath, true, true))
    // Clear the metrics
    testCassandraUtil(cassandraUtil)
    BaseMetricsReporter.gaugeMetrics.clear()
    jedis.flushDB()
    flinkCluster.before()
    updateRedis(jedis, EventFixture.CASE_1.asInstanceOf[Map[String, AnyRef]])
    updateRedis(jedis, EventFixture.CASE_2.asInstanceOf[Map[String, AnyRef]])
    updateRedis(jedis, EventFixture.CASE_3.asInstanceOf[Map[String, AnyRef]])
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    try {
      EmbeddedCassandraServerHelper.cleanEmbeddedCassandra()
      redisServer.stop()
    } catch {
      case ex: Exception => {
      }
    }
    flinkCluster.after()
  }
  
  "ActivityAgg " should " compute and update enrolment as completed when all the content consumption data processed" in {
    when(mockKafkaUtil.kafkaMapSource(courseAggregatorConfig.kafkaInputTopic)).thenReturn(new CompleteContentConsumptionMapSource)
    when(mockKafkaUtil.kafkaStringSink(courseAggregatorConfig.kafkaAuditEventTopic)).thenReturn(new auditEventSink)
    when(mockKafkaUtil.kafkaStringSink(courseAggregatorConfig.kafkaFailedEventTopic)).thenReturn(new failedEventSink)
    when(mockKafkaUtil.kafkaStringSink(courseAggregatorConfig.kafkaCertIssueTopic)).thenReturn(new certificateIssuedEventsSink)
    new ActivityAggregateUpdaterStreamTask(courseAggregatorConfig, mockKafkaUtil, mockHttpUtil).process()
    BaseMetricsReporter.gaugeMetrics(s"${courseAggregatorConfig.jobName}.${courseAggregatorConfig.totalEventCount}").getValue() should be(3)
    BaseMetricsReporter.gaugeMetrics(s"${courseAggregatorConfig.jobName}.${courseAggregatorConfig.batchEnrolmentUpdateEventCount}").getValue() should be(3)
    BaseMetricsReporter.gaugeMetrics(s"${courseAggregatorConfig.jobName}.${courseAggregatorConfig.dbReadCount}").getValue() should be(3)
    BaseMetricsReporter.gaugeMetrics(s"${courseAggregatorConfig.jobName}.${courseAggregatorConfig.dbUpdateCount}").getValue() should be(6)
    BaseMetricsReporter.gaugeMetrics(s"${courseAggregatorConfig.jobName}.${courseAggregatorConfig.cacheHitCount}").getValue() should be(18)
    BaseMetricsReporter.gaugeMetrics(s"${courseAggregatorConfig.jobName}.${courseAggregatorConfig.processedEnrolmentCount}").getValue() should be(3)
    BaseMetricsReporter.gaugeMetrics(s"${courseAggregatorConfig.jobName}.${courseAggregatorConfig.enrolmentCompleteCount}").getValue() should be(1)
    BaseMetricsReporter.gaugeMetrics(s"${courseAggregatorConfig.jobName}.${courseAggregatorConfig.failedEventCount}").getValue() should be(0)
    BaseMetricsReporter.gaugeMetrics(s"${courseAggregatorConfig.jobName}.${courseAggregatorConfig.skipEventsCount}").getValue() should be(0)
    BaseMetricsReporter.gaugeMetrics(s"${courseAggregatorConfig.jobName}.${courseAggregatorConfig.cacheMissCount}").getValue() should be(0)

    auditEventSink.values.size() should be(4)
    auditEventSink.values.forEach(event => {
      println("AUDIT_TELEMETRY_EVENT: " + event)
    })
  }
}

private class CompleteContentConsumptionMapSource extends SourceFunction[util.Map[String, AnyRef]] {

  override def run(ctx: SourceContext[util.Map[String, AnyRef]]) {
    ctx.collect(jsonToMap(EventFixture.CC_EVENT1))
    ctx.collect(jsonToMap(EventFixture.CC_EVENT2))
    ctx.collect(jsonToMap(EventFixture.CC_EVENT3))
  }

  override def cancel() = {}

  def jsonToMap(json: String): util.Map[String, AnyRef] = {
    val gson = new Gson()
    gson.fromJson(json, new util.LinkedHashMap[String, AnyRef]().getClass).asInstanceOf[util.Map[String, AnyRef]]
  }

}


