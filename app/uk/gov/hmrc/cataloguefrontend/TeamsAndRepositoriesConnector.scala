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

import java.net.URLEncoder
import java.time.LocalDateTime

import play.api.Logger
import play.api.libs.json._
import uk.gov.hmrc.cataloguefrontend.config.WSHttp
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http._

//import uk.gov.hmrc.cataloguefrontend.JavaDateTimeJsonFormatter._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

object RepoType extends Enumeration {

  type RepoType = Value

  val Deployable, Library, Other = Value

  implicit val repoType = new Reads[RepoType] {
    override def reads(json: JsValue): JsResult[RepoType] = json match {
      case JsString(s) => JsSuccess(RepoType.withName(s))
    }
  }

}

case class Link(name: String, displayName: String, url: String)

case class Environment(name: String, services: Seq[Link])

case class RepositoryDetails(name: String,
                             description: String,
                             createdAt: LocalDateTime,
                             lastActive: LocalDateTime,
                             teamNames: Seq[String],
                             githubUrls: Seq[Link],
                             ci: Seq[Link],
                             environments: Option[Seq[Environment]],
                             repoType: RepoType.RepoType)

case class RepositoryDisplayDetails(name:String, createdAt: LocalDateTime, lastUpdatedAt: LocalDateTime, repoType : RepoType.RepoType)
object RepositoryDisplayDetails {
  implicit val repoDetailsFormat = Json.format[RepositoryDisplayDetails]
}

case class Team(name:String, firstActiveDate: Option[LocalDateTime], lastActiveDate: Option[LocalDateTime], repos: Option[Map[String, Seq[String]]])
object Team {
  implicit val format = Json.format[Team]
}


trait TeamsAndRepositoriesConnector extends ServicesConfig {
  type ServiceName = String
  type TeamName = String

  val http: HttpGet with HttpPost

  def teamsAndServicesBaseUrl: String

  implicit val linkFormats = Json.format[Link]
  implicit val environmentsFormats = Json.format[Environment]
  implicit val serviceFormats = Json.format[RepositoryDetails]

  val CacheTimestampHeaderName = "X-Cache-Timestamp"
  implicit val httpReads: HttpReads[HttpResponse] = new HttpReads[HttpResponse] {
    override def read(method: String, url: String, response: HttpResponse) = response
  }

  def allTeams(implicit hc: HeaderCarrier): Future[CachedList[Team]] = {
    http.GET[HttpResponse](teamsAndServicesBaseUrl + s"/api/teams").map {
      toCachedList[Team]
    }
  }

  def teamInfo(teamName: String)(implicit hc: HeaderCarrier): Future[Option[CachedItem[Team]]] = {
    val url = teamsAndServicesBaseUrl + s"/api/teams_with_details/${URLEncoder.encode(teamName, "UTF-8")}"

    http.GET[HttpResponse](url)
      .map {
        toCachedItemOption[Team]
      }.recover {
      case ex =>
        Logger.error(s"An error occurred getting teamInfo when connecting to $url: ${ex.getMessage}", ex)
        None
    }
  }

  def allServiceNamesWithDates(implicit hc: HeaderCarrier): Future[CachedList[RepositoryDisplayDetails]] = {
    http.GET[HttpResponse](teamsAndServicesBaseUrl + s"/api/services").map {
      toCachedList[RepositoryDisplayDetails]
    }
  }

  def allLibraryNamesWithDates(implicit hc: HeaderCarrier): Future[CachedList[RepositoryDisplayDetails]] = {
    http.GET[HttpResponse](teamsAndServicesBaseUrl + s"/api/libraries").map {
      toCachedList[RepositoryDisplayDetails]
    }
  }

  def allRepositories(implicit hc: HeaderCarrier): Future[CachedList[RepositoryDisplayDetails]] =
    http.GET[HttpResponse](teamsAndServicesBaseUrl + s"/api/repositories").map {
      toCachedList[RepositoryDisplayDetails]
    }

  def repositoryDetails(name: String)(implicit hc: HeaderCarrier): Future[Option[CachedItem[RepositoryDetails]]] = {
    http.GET[HttpResponse](teamsAndServicesBaseUrl + s"/api/repositories/$name").map {
      toCachedItemOption[RepositoryDetails]
    }
  }

  def teamsByService(serviceNames: Seq[String])(implicit hc: HeaderCarrier): Future[CachedItem[Map[ServiceName, Seq[TeamName]]]] = {
    http.POST[Seq[String], HttpResponse](teamsAndServicesBaseUrl + s"/api/services?teamDetails=true", serviceNames).map {
      toCachedItem[Map[ServiceName, Seq[TeamName]]]
    }
  }

  def allTeamsByService()(implicit hc: HeaderCarrier): Future[CachedItem[Map[ServiceName, Seq[TeamName]]]] = {
    http.GET[HttpResponse](teamsAndServicesBaseUrl + s"/api/services?teamDetails=true").map {
      toCachedItem[Map[ServiceName, Seq[TeamName]]]
    }
  }

  def toCachedItem[T](r: HttpResponse)(implicit fjs: play.api.libs.json.Reads[T]): CachedItem[T] =
    new CachedItem(
      r.json.as[T],
      r.header(CacheTimestampHeaderName).getOrElse("(None)"))

  def toCachedItemOption[T](r: HttpResponse)(implicit fjs: play.api.libs.json.Reads[T]): Option[CachedItem[T]] =
    r.status match {
      case 404 => None
      case 200 =>
        Some(new CachedItem(
          r.json.as[T],
          r.header(CacheTimestampHeaderName).getOrElse("(None)")))
    }

  def toCachedList[T](r: HttpResponse)(implicit fjs: play.api.libs.json.Reads[T]): CachedList[T] =
    new CachedList(
      r.json.as[Seq[T]],
      r.header(CacheTimestampHeaderName).getOrElse("(None)"))
}

object TeamsAndRepositoriesConnector extends TeamsAndRepositoriesConnector {
  override val http = WSHttp

  override def teamsAndServicesBaseUrl: String = baseUrl("teams-and-services")
}
