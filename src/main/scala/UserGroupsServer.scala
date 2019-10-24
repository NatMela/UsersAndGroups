import Controller.{GroupsController, UsersController}
import Services.SwaggerDocService

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.RouteConcatenation
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors


object UserGroupsServer extends App with UsersController with GroupsController with RouteConcatenation {

  implicit val system: ActorSystem = ActorSystem("UsersAndGroupsServer")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContext = system.dispatcher

  val routes = cors()(userRoutes ~ groupRoutes ~ SwaggerDocService.routes)

  val serverBinding: Future[Http.ServerBinding] = Http().bindAndHandle(routes, "localhost", 8081)

  serverBinding.onComplete {
    case Success(bound) =>
      println(s"Server online at http://${bound.localAddress.getHostString}:${bound.localAddress.getPort}/")
    case Failure(e) =>
      Console.err.println(s"Server could not start!")
      e.printStackTrace()
      system.terminate()
  }

  Await.result(system.whenTerminated, Duration.Inf)

}
