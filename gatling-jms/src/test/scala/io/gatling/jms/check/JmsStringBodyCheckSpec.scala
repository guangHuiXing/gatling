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

package io.gatling.jms.check

import java.util.{ HashMap => JHashMap }

import io.gatling.core.CoreDsl
import io.gatling.core.check.CheckResult
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.session.SessionSpec.EmptySession
import io.gatling.jms.MockMessage
import io.gatling.{ BaseSpec, ValidationValues }

import javax.jms.Message
import org.scalatest.prop.TableDrivenPropertyChecks

class JmsStringBodyCheckSpec extends BaseSpec with ValidationValues with MockMessage with CoreDsl with JmsCheckSupport with TableDrivenPropertyChecks {
  override implicit val configuration: GatlingConfiguration = GatlingConfiguration.loadForTest()

  private def jmap[K, V] = new JHashMap[K, V]

  private val testResponses = Table(
    ("msg", "msgType"),
    (textMessage("""[{"id":"1072920417"},"id":"1072920418"]"""), "TextMessage"),
    (bytesMessage("""[{"id":"1072920417"},"id":"1072920418"]""".getBytes(configuration.core.charset)), "BytesMessage")
  )

  forAll(testResponses) { (response: Message, msgType: String) =>
    s"bodyString.find.exists for $msgType" should "extract response body correctly" in {
      bodyString.find.exists
        .check(response, EmptySession, jmap[Any, Any])
        .succeeded shouldBe CheckResult(Some("""[{"id":"1072920417"},"id":"1072920418"]"""), None)
    }

    s"bodyString.notNull for $msgType" should "pass when response not empty" in {
      bodyString.find.notNull
        .check(response, EmptySession, jmap[Any, Any])
        .succeeded shouldBe CheckResult(Some("""[{"id":"1072920417"},"id":"1072920418"]"""), None)
    }

    s"bodyString.isNull for $msgType" should "fail when response not empty" in {
      bodyString.isNull
        .check(response, EmptySession, jmap[Any, Any])
        .failed shouldBe """bodyString.find.isNull, found [{"id":"1072920417"},"id":"1072920418"]"""
    }
  }
}
