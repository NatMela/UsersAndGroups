package Controller

import java.sql.Date

import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._
import Services.UsersService
import akka.http.scaladsl.marshalling.{Marshal, PredefinedToResponseMarshallers, ToResponseMarshallable}
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import com.google.inject.{Guice, Injector}
import io.swagger.annotations._
import javax.ws.rs.Path
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContextExecutor
import scala.util.Success

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

@Path("/users")
@Api(value = "Users Controller")
trait UsersController extends JsonSupport {

  implicit def system: ActorSystem
  implicit def executor: ExecutionContextExecutor

  lazy val logger =LoggerFactory.getLogger(classOf[UsersController])

  val defaultNumberOfUsersOnPage = 20
  val defaultPageNumberForUsers = 1
  val maxPageSizeForUsers = 100

  object UserService {
//    lazy val injector: Injector = Guice.createInjector()
//    lazy val service = injector.getInstance(classOf[UsersService])
    val service = new UsersService()
  }


  @ApiOperation(value = "Get all users", httpMethod = "GET", response = classOf[UsersDTO])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Bad request passed to the endpoint"),
    new ApiResponse(code = 200, message = "Step performed successfully")
  ))
  @Path("/all")
  def getAllUsers: Route =
    pathEnd {
      get {
        complete(UserService.service.getUsers())
      }
    }

  @ApiOperation(value = "Get users from particular page", httpMethod = "GET", response = classOf[UsersDTO])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "pageNumber", required = false, dataType = "number", paramType = "query", value = "page number (starts from 1)"),
    new ApiImplicitParam(name = "pageSize", required = false, dataType = "number", paramType = "query", value = "number of items shown per page")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Bad request passed to the endpoint"),
    new ApiResponse(code = 200, message = "Step performed successfully")
  ))
  @Path("/")
  def getUsersFromPage: Route =
    pathEnd {
      get {
        parameterMultiMap { params =>
          val pageSize = params.get("pageSize").flatMap(_.headOption).map(_.toInt).getOrElse(0)
          val pageNumber = params.get("pageNumber").flatMap(_.headOption).map(_.toInt).getOrElse(0)
          if ((pageNumber > 0) && (pageSize > 0)) {
            if (pageSize > maxPageSizeForUsers) {
              complete(UserService.service.getUsersFromPage(maxPageSizeForUsers, pageNumber))
            } else
              complete(UserService.service.getUsersFromPage(pageSize, pageNumber))
          } else {
            complete(UserService.service.getUsersFromPage(defaultNumberOfUsersOnPage, defaultPageNumberForUsers))
          }
        }
      }
    }


  @ApiOperation(value = "Get user by Id", httpMethod = "GET", response = classOf[UsersDTO])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "id", required = true, dataType = "integer", paramType = "path", value = "User Id")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Bad request passed to the endpoint"),
    new ApiResponse(code = 200, message = "Step performed successfully")
  ))
  @Path("/{id}")
  def getUserById(@ApiParam(hidden = true) id: Int): Route =
    pathEnd {
      get {
          complete(UserService.service.getUserById(id))
      }
    }


  @ApiOperation(value = "Update user by Id", httpMethod = "PUT", response = classOf[UsersDTO])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "id", required = true, dataType = "integer", paramType = "path", value = "User Id"),
    new ApiImplicitParam(name = "userRow", required = true, dataType = "UsersDTO", paramType = "body", value = "Row to update users information")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Bad request passed to the endpoint"),
    new ApiResponse(code = 200, message = "Step performed successfully")
  ))
  @Path("/{id}")
  def updateUserById(@ApiParam(hidden = true) id: Int): Route =
    pathEnd {
      put {
        entity(as[UsersDTO]){userRow =>
          complete(UserService.service.updateUserById(id, userRow))
        }
      }
    }

  @ApiOperation(value = "Insert user", httpMethod = "POST", response = classOf[UsersDTO])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "userRow", required = true, dataType = "UsersDTO", paramType = "body", value = "Row to insert")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Bad request passed to the endpoint"),
    new ApiResponse(code = 200, message = "Step performed successfully")
  ))
    @Path("/")
  def insertUser(): Route =
      pathEnd {
      post {
        entity(as[UsersDTO]){userRow =>
          complete(UserService.service.insertUser(userRow))
        }
      }
    }

  @ApiOperation(value = "Get information about groups for user with given id ", httpMethod = "GET", response = classOf[UserWithGroupsDTO])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "id", required = true, dataType = "integer", paramType = "path", value = "User Id")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Bad request passed to the endpoint"),
    new ApiResponse(code = 200, message = "Step performed successfully")
  ))
  @Path("/{id}/details")
  def getUserDetails(@ApiParam(hidden = true) id: Int): Route =
    pathEnd {
      get {
        complete {
          UserService.service.getDetailsForUser(id)
        }
      }
    }

  lazy val userRoutes: Route = {
    pathPrefix("users") {
      getUsersFromPage ~
        insertUser() ~
        pathPrefix("all") {
          getAllUsers
        } ~
        pathPrefix(IntNumber) { id =>
          getUserById(id) ~
          updateUserById(id) ~
            pathPrefix("details") {
              getUserDetails(id)
            }
        }
    }
  }
}


