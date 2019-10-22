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

case class UsersDTO(id: Option[Int], firstName: String, lastName: String, createdAt: String, isActive: Boolean)

case class Users(users: Seq[UsersDTO])

case class GroupsDTO(id: Option[Int], title: String, createdAt: String, description: String)

case class UserWithGroupsDTO(userInfo: UsersDTO, groups: Seq[GroupsDTO])

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val userFormat = jsonFormat5(UsersDTO)
  implicit val usersFormat = jsonFormat1(Users)
  implicit val groupsFormat = jsonFormat4(GroupsDTO)
  implicit val userGroupsFormat = jsonFormat2(UserWithGroupsDTO)
}

@Path("/groups")
@Api(value = "Groups Controller")
trait GroupsController extends JsonSupport {

  implicit def system: ActorSystem

  lazy val log: LoggingAdapter = Logging(system, classOf[UsersController])

  implicit lazy val timeout: Timeout = Timeout(5.seconds)

  val defaultNumberOfUsersOnPage = 20
  val defaultPageNumber = 1
  val maxPageSize = 100

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
            if (pageSize > maxPageSize) {
              complete(GroupsService.service.getGroupsFromPage(maxPageSize, pageNumber))
            } else
              complete(GroupsService.service.getGroupsFromPage(pageSize, pageNumber))
          } else {
            complete(GroupsService.service.getGroupsFromPage(defaultNumberOfUsersOnPage, defaultPageNumber))
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
