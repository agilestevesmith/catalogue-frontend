/*
 * Copyright 2016 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.cataloguefrontend

import com.github.tomakehurst.wiremock.http.RequestMethod._
import org.scalatest._
import org.scalatestplus.play.OneServerPerTest
import play.api.libs.ws.WS
import play.api.test.FakeApplication
import uk.gov.hmrc.play.test.UnitSpec

class TeamServicesSpec extends UnitSpec with BeforeAndAfter with OneServerPerTest with WireMockEndpoints{

  override def newAppForTest(testData: TestData): FakeApplication = new FakeApplication(
    additionalConfiguration = Map(
      "microservice.services.catalogue.port" -> endpointPort
  ))

  "landing page" should {
    "show a list of teams" in  {
      catalogEndpoint(GET, "/catalogue/api/teams", willRespondWith = (200, Some(
        """[{
          | "teamName": "GMP",
          | "repositories": [
          |   {
          |    "name": "attachments-example",
          |    "url": "http://example.com/DDCN/attachments-example",
          |    "isMicroservice": true
          |   },
          |   {
          |    "name": "contacts-manager-acceptance-tests",
          |    "url": "http://example.com/DDCN/contacts-manager-acceptance-tests",
          |    "isMicroservice": false
          |   }
          | ]
          |}]""".stripMargin
      )))

      val response = await(WS.url(s"http://localhost:$port/catalogue/GMP/services").get)

      response.status shouldBe 200
      response.body should include("<li>attachments-example</li>")
      response.body should not include "<li>contacts-manager-acceptance-tests</li>"
    }
  }
}