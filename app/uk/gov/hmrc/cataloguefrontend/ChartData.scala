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

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import play.twirl.api.Html

import scala.xml.{Elem, NodeSeq}

case class ChartData(rows: Seq[Html]) {
  def isEmpty = rows.isEmpty
}

object ChartData {



  def deploymentThroughput(serviceName: String, dataPoints: Option[Seq[DeploymentThroughputDataPoint]]): Option[ChartData] = {

    dataPoints.map { points =>
      ChartData(chartRowsThroughput(serviceName, points))
    }
  }

  def deploymentStability(serviceName: String, dataPoints: Option[Seq[DeploymentStabilityDataPoint]]): Option[ChartData] = {

    dataPoints.map { points =>
      ChartData(chartRowsStability(serviceName, points))
    }
  }




  private def chartRowsThroughput(serviceName: String, points: Seq[DeploymentThroughputDataPoint]): Seq[Html] = {
    for {
      dp <- points

    } yield {
      val leadTimeToolTip = toolTip(dp.period, "Lead Time", dp.leadTime, Some(getReleaseUrlAnchor(serviceName, dp)))
      val intervalToolTip = toolTip(dp.period, "Interval", dp.interval, Some(getReleaseUrlAnchor(serviceName, dp)))

      Html(s"""["${dp.period}", ${unwrapMedian(dp.leadTime)}, "$leadTimeToolTip", ${unwrapMedian(dp.interval)}, "$intervalToolTip"]""")
    }
  }

  private def chartRowsStability(serviceName: String, points: Seq[DeploymentStabilityDataPoint]): Seq[Html] = {
    for {
      dp <- points

    } yield {

      Html(s"""["${dp.period}", ${unwrap(dp.hotfixRate)}, ${unwrapMedian(dp.hotfixLeadTime)}]""")
    }
  }

  private def getReleaseUrlAnchor(serviceName: String, dp: DeploymentThroughputDataPoint) = <a href={releasesUrl(serviceName, dateToString(dp.from), dateToString(dp.to))} >View releases</a>

  private def releasesUrl(serviceName: String, from: String, to: String) = s"${uk.gov.hmrc.cataloguefrontend.routes.CatalogueController.releases().url}?serviceName=${serviceName}&from=${from}&to=${to}"


  private def dateToString(date: LocalDate) = date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))

  private def unwrapMedian(container: Option[MedianDataPoint]) = container.map(l => s"""${l.median}""").getOrElse("null")
  private def unwrap(container: Option[_]) = container.map(l => s"""$l""").getOrElse("null")


  private def toolTip(period: String, dataPointLabel: String, dataPoint: Option[MedianDataPoint], additionalContent: Option[NodeSeq]) = {

    val element: Elem =
      <div>
        <table>
          <tr>
            <td nowrap="true">
              <label>Period:</label> {period}
            </td>

          </tr>
          <tr>
            <td nowrap="true">
              <label>{dataPointLabel}:</label> {dataPoint.fold("")(_.median.toString)}
            </td>

          </tr>
          <tr>
            <td colspan="2" align="center">
              {additionalContent.getOrElse(NodeSeq.Empty)}
            </td>
          </tr>
        </table>
      </div>

    Html(element.mkString.replaceAll("\"", "'").replaceAll("\n", ""))
  }

}
