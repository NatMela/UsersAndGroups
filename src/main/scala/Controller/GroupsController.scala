package Controller

import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._
import Services.GroupsService
import akka.event.{Logging, LoggingAdapter}
import akka.util.Timeout

import scala.concurrent.duration._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import io.swagger.annotations._
import javax.ws.rs.Path

@Path("/groups")
@Api(value = "Groups Controller")
trait GroupsController extends JsonSupport {

  implicit def system: ActorSystem

  lazy val log: LoggingAdapter = Logging(system, classOf[GroupsController])

  implicit lazy val timeout: Timeout = Timeout(5.seconds)

  val defaultNumberOfGroupsOnPage = 20
  val defaultPageNumberForGroups = 1
  val maxPageSizeForGroups = 100

  object GroupsService {
    val service = new GroupsService()
  }

  @ApiOperation(value = "Get all groups", httpMethod = "GET", response = classOf[GroupsDTO])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Bad request passed to the endpoint"),
    new ApiResponse(code = 200, message = "Step performed successfully")
  ))
  @Path("/all")
  def getAllGroups: Route =
    pathEnd {
      get {
        complete(GroupsService.service.getGroups)
      }
    }

  @ApiOperation(value = "Get groups from particular page", httpMethod = "GET", response = classOf[GroupsDTO])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "pageNumber", required = false, dataType = "number", paramType = "query", value = "page number (starts from 1)"),
    new ApiImplicitParam(name = "pageSize", required = false, dataType = "number", paramType = "query", value = "number of items shown per page")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Bad request passed to the endpoint"),
    new ApiResponse(code = 200, message = "Step performed successfully")
  ))
  @Path("/")
  def getGroupsFromPage: Route =
    pathEnd {
      get {
        parameterMultiMap { params =>
          val pageSize = params.get("pageSize").flatMap(_.headOption).map(_.toInt).getOrElse(0)
          val pageNumber = params.get("pageNumber").flatMap(_.headOption).map(_.toInt).getOrElse(0)
          if ((pageNumber > 0) && (pageSize > 0)) {
            if (pageSize > maxPageSizeForGroups) {
              complete(GroupsService.service.getGroupsFromPage(maxPageSizeForGroups, pageNumber))
            } else
              complete(GroupsService.service.getGroupsFromPage(pageSize, pageNumber))
          } else {
            complete(GroupsService.service.getGroupsFromPage(defaultNumberOfGroupsOnPage, defaultPageNumberForGroups))
          }
        }
      }
    }

  lazy val groupRoutes: Route = {
    pathPrefix("groups") {
      getGroupsFromPage ~
        pathPrefix("all") {
          getAllGroups
        }
    }
  }
}
