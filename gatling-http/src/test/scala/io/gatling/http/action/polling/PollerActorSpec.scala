/*
 * Copyright 2011-2019 GatlingCorp (https://gatling.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.gatling.http.action.polling

import scala.concurrent.duration._

import io.gatling.AkkaSpec
import io.gatling.commons.util.DefaultClock
import io.gatling.core.session._
import io.gatling.core.session.SessionSpec.EmptySession
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.stats.DataWritersStatsEngine
import io.gatling.core.stats.writer.{ ErrorMessage, Init, RunMessage }
import io.gatling.http.cache.HttpCaches
import io.gatling.http.engine.HttpEngine
import io.gatling.http.engine.tx.HttpTxExecutor
import io.gatling.http.protocol.HttpProtocol
import io.gatling.http.request.HttpRequestDef
import io.gatling.http.response.ResponseBuilderFactory

import akka.testkit._

// TODO : test resourceFetched, stopPolling
class PollerActorSpec extends AkkaSpec {

  private implicit val configuration: GatlingConfiguration = GatlingConfiguration.loadForTest()
  private val requestName = "foo".expressionSuccess
  private val clock = new DefaultClock

  private def newHttpRequestDef = HttpRequestDef(requestName, failedExpr, null)

  "PollerActor" should "start in Uninitalized state with NoData" in {
    val dataWriterProbe = TestProbe()
    val poller = createPollerActor(1.second, newHttpRequestDef, mock[HttpEngine], dataWriterProbe)

    poller.stateName shouldBe Uninitialized
    poller.stateData shouldBe NoData
  }

  it should "after receiving a StartPolling, move to the Polling state with the initial session" in {
    val dataWriterProbe = TestProbe()
    val poller = createPollerActor(1.second, newHttpRequestDef, mock[HttpEngine], dataWriterProbe)

    poller ! StartPolling(EmptySession)

    poller.isTimerActive(PollerActor.PollTimerName) shouldBe true
    poller.stateName shouldBe Polling
    poller.stateData shouldBe a[PollingData]
    poller.stateData shouldBe PollingData(EmptySession)
  }

  it should "do nothing if the request name could not be resolved and fail the session" in {
    val dataWriterProbe = TestProbe()
    val mockHttpEngine = mock[HttpEngine]
    val poller = createPollerActor(1.second, newHttpRequestDef, mockHttpEngine, dataWriterProbe)

    poller ! StartPolling(EmptySession)
    Thread.sleep(2.seconds.toMillis)

    poller.stateName shouldBe Polling
    poller.stateData shouldBe a[PollingData]
    val pollingData = poller.stateData.asInstanceOf[PollingData]
    pollingData.session.isFailed shouldBe true
  }

  it should "do nothing if the request could not be resolved, fail the session and report to the DataWriters" in {
    val dataWriterProbe = TestProbe()
    val mockHttpEngine = mock[HttpEngine]
    val poller = createPollerActor(1.second, newHttpRequestDef, mockHttpEngine, dataWriterProbe)

    poller ! StartPolling(EmptySession)
    Thread.sleep(2.seconds.toMillis)

    poller.stateName shouldBe Polling
    poller.stateData shouldBe a[PollingData]
    val pollingData = poller.stateData.asInstanceOf[PollingData]
    pollingData.session.isFailed shouldBe true

    dataWriterProbe.expectMsgType[ErrorMessage]
  }

  private def createPollerActor(
      period: FiniteDuration,
      requestDef: HttpRequestDef,
      httpEngine: HttpEngine,
      dataWriterProbe: TestProbe
  ) =
    TestFSMRef(
      new PollerActor(
        pollerName = "testPoller",
        period = period,
        requestDef = requestDef,
        responseBuilderFactory = mock[ResponseBuilderFactory],
        httpTxExecutor = mock[HttpTxExecutor],
        statsEngine = new DataWritersStatsEngine(
          Init(
            Nil,
            RunMessage(
              "simulationClassName",
              "simulationId",
              0,
              "runDescription",
              "gatlingVersion"
            ),
            Nil
          ),
          List(dataWriterProbe.ref),
          system,
          clock
        ),
        clock = clock,
        httpCaches = mock[HttpCaches],
        httpProtocol = HttpProtocol(configuration),
        charset = configuration.core.charset
      )
    )

  def failedExpr[T]: Expression[T] = "Failed expression".expressionFailure
}
