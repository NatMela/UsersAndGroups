package controller

import akka.http.scaladsl.marshalling.Marshal
import services.GroupsService
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import com.google.inject.{Guice, Inject}
import config.{Db, DiModule}
import dao.{GroupsDAO, UserDAO, UserGroupsDAO}
import io.pileworx.akka.http.rest.hal.{Link, ResourceBuilder}
import io.swagger.annotations._
import javax.ws.rs.Path
import org.slf4j.LoggerFactory
import io.pileworx.akka.http.rest.hal.Relations._
import spray.json._

import scala.concurrent.ExecutionContext

trait TestLinks extends JsonSupport {
  def testLink(rel: String, id: String): (String, Link) = rel -> Link(href = s"/groups/$id")
  def testsLink(rel: String): (String, Link) = rel -> Link(href = s"/groups")
  def testsLink1(rel: String, id: String): (String, Link) = rel -> Link(href = s"/groups/$id/details")

  def newResource(id: String): JsValue = {
    ResourceBuilder(
      withLinks = Some(Map(
        testLink(Self, id),
        testsLink(Up)
      ))
    ).build
  }

  def toResources(groups: Seq[GroupsDTO]): JsValue = {
    ResourceBuilder(
      withEmbedded = Some(Map(
        "groups" -> groups.map(f => toResource(f))
      )),
      withLinks = Some(Map(testsLink(Self)))
    ).build
  }

  def toResource(group: GroupsDTO): JsValue = {
    ResourceBuilder(
      withData = Some(group.toJson),
      withLinks = Some(Map(
        testLink(Self, group.id.get.toString),
        testsLink(Up),
        testsLink1(Next, group.id.get.toString)
      ))
    ).build
  }
}


@Path("/groups")
@Api(value = "Groups Controller")
class GroupsController @Inject()(userDAO: UserDAO, groupsDAO: GroupsDAO, userGroupsDAO: UserGroupsDAO, dbConfig: Db) extends JsonSupport with TestLinks {

  lazy val log = LoggerFactory.getLogger(classOf[GroupsController])

  val defaultNumberOfGroupsOnPage = 20
  val defaultPageNumberForGroups = 1
  val maxPageSizeForGroups = 100
  implicit val ec: ExecutionContext = ExecutionContext.global

  val inject = Guice.createInjector(new DiModule())
  val service = inject.getInstance(classOf[GroupsService])

  @ApiOperation(value = "Get all groups", httpMethod = "GET", response = classOf[GroupsDTO])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Bad request passed to the endpoint"),
    new ApiResponse(code = 200, message = "Step performed successfully")
  ))
  @Path("/all")
  def getAllGroups: Route =
    pathEnd {
      get {
        complete(service.getGroups)
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
              complete(service.getGroupsFromPage(maxPageSizeForGroups, pageNumber))
            } else
              complete(service.getGroupsFromPage(pageSize, pageNumber))
          } else {
            complete(service.getGroupsFromPage(defaultNumberOfGroupsOnPage, defaultPageNumberForGroups))
          }
        }
      }
    }

  @ApiOperation(value = "Get group by Id", httpMethod = "GET", response = classOf[GroupsDTO])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "id", required = true, dataType = "integer", paramType = "path", value = "Group Id")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Bad request passed to the endpoint"),
    new ApiResponse(code = 200, message = "Step performed successfully"),
    new ApiResponse(code = 204, message = "No group with such id was found")
  ))
  @Path("/{id}")
  def getGroupById(@ApiParam(hidden = true) id: Int): Route =
    pathEnd {
      get {
        complete {
          (service.getGroupById(id)).map{
            case Some(response) => Marshal(toResource((response))).to[HttpResponse]
            case _ => Marshal(StatusCodes.NoContent).to[HttpResponse]
                     }

        }
      }
    }

  @ApiOperation(value = "Update group by Id", httpMethod = "PUT", response = classOf[GroupsDTO])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "id", required = true, dataType = "integer", paramType = "path", value = "Group Id"),
    new ApiImplicitParam(name = "groupRow", required = true, dataType = "controller.GroupsDTO", paramType = "body", value = "Row to update group information")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Bad request passed to the endpoint"),
    new ApiResponse(code = 200, message = "Step performed successfully"),
    new ApiResponse(code = 204, message = "No group with such id was found")
  ))
  @Path("/{id}")
  def updateGroupById(@ApiParam(hidden = true) id: Int): Route =

    pathEnd {
      put {
        entity(as[GroupsDTO]) { groupRow =>
          onComplete(service.updateGroupById(id, groupRow)) {
            case util.Success(Some(response)) => complete(StatusCodes.OK, response)
            case util.Success(None) => complete(StatusCodes.NoContent)
            case util.Failure(ex) => complete(StatusCodes.BadRequest, s"An error occurred: ${ex.getMessage}")
          }
        }
      }
    }

  @ApiOperation(value = "Delete group by Id", httpMethod = "DELETE", response = classOf[String])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "id", required = true, dataType = "integer", paramType = "path", value = "Group Id")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Bad request passed to the endpoint"),
    new ApiResponse(code = 200, message = "Step performed successfully")
  ))
  @Path("/{id}")
  def deleteGroup(id: Int): Route =
    pathEnd {
      delete {
        onComplete(service.deleteGroup(id)) {
          case util.Success(_) => complete(StatusCodes.OK)
          case util.Failure(ex) => complete(StatusCodes.NotFound, s"An error occurred: ${ex.getMessage}")
        }
      }
    }

  @ApiOperation(value = "Delete group for user", httpMethod = "DELETE", response = classOf[String])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "groupId", required = true, dataType = "integer", paramType = "path", value = "Group Id"),
    new ApiImplicitParam(name = "userId", required = true, dataType = "integer", paramType = "path", value = "User Id")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Bad request passed to the endpoint"),
    new ApiResponse(code = 200, message = "Step performed successfully")
  ))
  @Path("/{groupId}/users/{userId}")
  def deleteGroupForUser(groupId: Int, userId: Int): Route =
    pathEnd {
      delete {
        onComplete(service.deleteGroupForUser(userId, groupId)) {
          case util.Success(_) => complete(StatusCodes.OK)
          case util.Failure(ex) => complete(StatusCodes.NotFound, s"An error occurred: ${ex.getMessage}")
        }
      }
    }


  @ApiOperation(value = "Insert group", httpMethod = "POST", response = classOf[UsersDTO])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "groupRow", required = true, dataType = "controller.GroupsDTO", paramType = "body", value = "Row to insert")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Bad request passed to the endpoint"),
    new ApiResponse(code = 201, message = "Step performed successfully")
  ))
  @Path("/")
  def insertGroup(): Route =
    pathEnd {
      post {
        entity(as[GroupsDTO]) { groupRow =>
          onComplete(service.insertGroup(groupRow)) {
            case util.Success(Some(response)) => complete(StatusCodes.Created, response)
            case util.Success(None) => complete(StatusCodes.BadRequest, s"User was not inserted")
            case util.Failure(ex) => complete(StatusCodes.BadRequest, s"An error occurred: ${ex.getMessage}")
          }
        }
      }
    }

  @ApiOperation(value = "Add group to user", httpMethod = "POST", response = classOf[String])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "groupId", required = true, dataType = "integer", paramType = "path", value = "Group Id"),
    new ApiImplicitParam(name = "userId", required = true, dataType = "integer", paramType = "path", value = "User Id")

  ))
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Bad request passed to the endpoint"),
    new ApiResponse(code = 200, message = "Step performed successfully")
  ))
  @Path("/{groupId}/users/{userId}")
  def addGroupForUser(@ApiParam(hidden = true) groupId: Int, @ApiParam(hidden = true) userId: Int): Route =
    pathEnd {
      post {
        onComplete(service.addGroupToUser(userId, groupId)) {
          case util.Success(response) => {
            response match {
              case "" => complete(StatusCodes.OK)
              case _ => complete(StatusCodes.BadRequest, response)
            }
          }
          case util.Failure(ex) => complete(StatusCodes.BadRequest, s"An error occurred: ${ex.getMessage}")
        }
      }
    }

  @ApiOperation(value = "Get information about users for group with given id ", httpMethod = "GET", response = classOf[GroupWithUsersDTO])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "id", required = true, dataType = "integer", paramType = "path", value = "Group Id")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Bad request passed to the endpoint"),
    new ApiResponse(code = 200, message = "Step performed successfully"),
    new ApiResponse(code = 204, message = "No user with such id was found")
  ))
  @Path("/{id}/details")
  def getGroupDetails(@ApiParam(hidden = true) id: Int): Route =
    pathEnd {
      get {
        onComplete(service.getDetailsForGroup(id)) {
          case util.Success(Some(response)) => complete(StatusCodes.OK, response)
          case util.Success(None) => complete(StatusCodes.NoContent)
          case util.Failure(ex) => complete(StatusCodes.BadRequest, s"An error occurred: ${ex.getMessage}")
        }
      }
    }

  lazy val groupRoutes: Route = {
    pathPrefix("groups") {
      getGroupsFromPage ~
        insertGroup() ~
        pathPrefix("all") {
          getAllGroups
        } ~
        pathPrefix(IntNumber) { userId =>
          getGroupById(userId) ~
            updateGroupById(userId) ~
            deleteGroup(userId) ~
            pathPrefix("users") {
              pathPrefix(IntNumber) { groupId =>
                deleteGroupForUser(groupId, userId) ~
                  addGroupForUser(userId, groupId)
              }
            } ~
            pathPrefix("details") {
              getGroupDetails(userId)
            }
        }
    }
  }
}
