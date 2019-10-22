package DAO

import Controller.UsersController
import com.github.swagger.akka.SwaggerHttpService
import com.github.swagger.akka.model.Info
import akka.actor.ActorSystem
import akka.http.scaladsl.server.Route
import akka.parboiled2.RuleTrace.NotPredicate.Base
import com.typesafe.config.ConfigFactory
import io.swagger.models.ExternalDocs
import io.swagger.models.ExternalDocs._

object SwaggerDocService extends SwaggerHttpService {
  override val apiClasses = Set(classOf[UsersController])
  override val host = "localhost:8081"
  override val info = Info(version = "1.0")
  override val externalDocs = Some(new ExternalDocs().description("Core Docs").url("http://acme.com/docs"))

  override val unwantedDefinitions = Seq("Function1", "Function1RequestContextFutureRouteResult")


  override def routes: Route = super.routes ~ get {
    pathPrefix("") {
      pathEndOrSingleSlash {
        getFromResource("swagger-ui/index.html")
      }
    } ~
     getFromResourceDirectory("swagger-ui")
  }
}
