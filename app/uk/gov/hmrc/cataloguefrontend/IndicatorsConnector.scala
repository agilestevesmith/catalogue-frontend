/*
 * Copyright 2017 HM Revenue & Customs
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

import java.time.LocalDate
import play.api.Logger
import play.api.libs.json.Json
import uk.gov.hmrc.cataloguefrontend.config.WSHttp
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet, HttpReads, HttpResponse}

import scala.concurrent.Future


case class MedianDataPoint(median: Int)

case class DeploymentThroughputDataPoint(period: String, from: LocalDate, to: LocalDate, leadTime: Option[MedianDataPoint], interval: Option[MedianDataPoint])

case class DeploymentStabilityDataPoint(period: String, from: LocalDate, to: LocalDate, hotfixRate: Option[Double], hotfixInterval: Option[MedianDataPoint])

case class DeploymentIndicators(throughput: Seq[DeploymentThroughputDataPoint], stability: Seq[DeploymentStabilityDataPoint])

case class DeploymentsMetricResult(period: String,
                                   from: LocalDate,
                                   to: LocalDate,
                                   throughput: Option[Throughput],
                                   stability: Option[Stability])


case class Throughput(leadTime: Option[MeasureResult], interval: Option[MeasureResult])

case class Stability(hotfixRate: Option[Double], hotfixInterval: Option[MeasureResult])

case class MeasureResult(median: Int)


trait IndicatorsConnector extends ServicesConfig {
  val http: HttpGet
  def indicatorsBaseUrl: String

  import JavaDateTimeJsonFormatter._

  implicit val mesureResultFormats = Json.reads[MeasureResult]
  implicit val throughputFormats = Json.reads[Throughput]
  implicit val stabilityFormats = Json.reads[Stability]
  implicit val deploymentsMetricResultFormats = Json.reads[DeploymentsMetricResult]



  implicit val httpReads: HttpReads[HttpResponse] = new HttpReads[HttpResponse] {
    override def read(method: String, url: String, response: HttpResponse) = response
  }

  def deploymentIndicatorsForTeam(teamName :String)(implicit hc: HeaderCarrier) = deploymentIndicators(s"/team/$teamName/deployments")
  def deploymentIndicatorsForService(serviceName :String)(implicit hc: HeaderCarrier) = deploymentIndicators(s"/service/$serviceName/deployments")

  private def deploymentIndicators(path: String)(implicit hc: HeaderCarrier): Future[Option[DeploymentIndicators]] = {
    val url = indicatorsBaseUrl + path
    val eventualResponse: Future[HttpResponse] = http.GET[HttpResponse](url)
    eventualResponse.map { r =>
      r.status match {
        case 404 => Some(DeploymentIndicators(Nil, Nil))
        case 200 =>

          val deploymentsMetricResults: Seq[DeploymentsMetricResult] = r.json.as[Seq[DeploymentsMetricResult]]
          val (deploymentThroughputs, deploymentStabilities) =
            deploymentsMetricResults.map { dmr =>

              val leadTime = dmr.throughput.flatMap(x => x.leadTime.map(y => MedianDataPoint.apply(y.median)))
              val interval = dmr.throughput.flatMap(x => x.interval.map(y => MedianDataPoint.apply(y.median)))
              val hotfixRate = dmr.stability.flatMap(_.hotfixRate)
              val hotfixLeadTime = dmr.stability.flatMap(x => x.hotfixInterval.map(y => MedianDataPoint.apply(y.median)))

              (DeploymentThroughputDataPoint(dmr.period, dmr.from, dmr.to, leadTime, interval),
                DeploymentStabilityDataPoint(dmr.period, dmr.from, dmr.to, hotfixRate, hotfixLeadTime))

            }.unzip

          Some(DeploymentIndicators(deploymentThroughputs, deploymentStabilities))
      }
    }.recover {
      case ex =>
        Logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
        None
    }
  }
}

object IndicatorsConnector extends IndicatorsConnector {
  override def indicatorsBaseUrl: String = baseUrl("indicators") + "/api/indicators"
  override val http = WSHttp
}
