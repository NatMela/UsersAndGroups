package config

import com.google.inject.AbstractModule
import dao.{GroupsDAO, UserDAO, UserGroupsDAO}
import javax.inject.Singleton

import scala.concurrent.ExecutionContext

@Singleton
class DiEC{
  val executionContext: ExecutionContext = ExecutionContext.global
}

class DiModule extends AbstractModule {
  override def configure() = {
    bind(classOf[Db]).to(classOf[PostgresDB])
    bind(classOf[PostgresDB]).asEagerSingleton()
    bind(classOf[UserDAO]).asEagerSingleton()
    bind(classOf[GroupsDAO]).asEagerSingleton()
    bind(classOf[UserGroupsDAO]).asEagerSingleton()
    bind(classOf[DiEC]).asEagerSingleton()
  }
}

