package Controller

import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._
import Services.UsersService
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.model.{HttpResponse, StatusCode}
import akka.util.Timeout

import scala.concurrent.duration._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.RouteDirectives.complete

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


trait UsersController extends JsonSupport {

  implicit def system: ActorSystem

  lazy val log: LoggingAdapter = Logging(system, classOf[UsersController])

  implicit lazy val timeout: Timeout = Timeout(5.seconds)

  object UserService {
    val service = new UsersService()
  }

  def getUsersFromPage: Route =
    pathEnd {
      parameterMultiMap { params =>
        val pageSize = params.get("pageSize").flatMap(_.headOption).map(_.toInt).getOrElse(0)
        val pageNumber = params.get("pageNumber").flatMap(_.headOption).map(_.toInt).getOrElse(0)
        if ((pageNumber > 0) && (pageSize > 0)) {
          complete(UserService.service.getUsersFromPage(pageSize, pageNumber))
        } else {
          complete(UserService.service.getUsers())
        }
      }
    }

  def getUserById(id: Int): Route =
    pathEnd {
      complete {
        UserService.service.getUserById(id)
      }
    }

  def getUserDetails(id: Int): Route =
    pathEnd {
      complete {
        UserService.service.getDetailsForUser(id)
      }
    }

  lazy val userRoutes: Route = {
    pathPrefix("users") {
      getUsersFromPage ~
        pathPrefix(IntNumber) { id =>
          getUserById(id) ~
            pathPrefix("details") {
              getUserDetails(id)
            }
        }
    }
  }
}


