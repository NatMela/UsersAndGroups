package config

import com.google.inject.AbstractModule
import dao.{GroupsDAO, UserDAO, UserGroupsDAO}

class DiModule extends AbstractModule {
  override def configure() = {
    bind(classOf[Db]).to(classOf[PostgresDB])
    bind(classOf[PostgresDB]).asEagerSingleton()
    bind(classOf[UserDAO]).asEagerSingleton()
    bind(classOf[GroupsDAO]).asEagerSingleton()
    bind(classOf[UserGroupsDAO]).asEagerSingleton()
  }
}

