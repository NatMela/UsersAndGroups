package Config

// GuiceModule
import DAO.{GroupsDAO, UserDAO, UserGroupsDAO}
import com.google.inject.AbstractModule

class GuiceModule extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[Db]).to(classOf[PostgresDB])
//    bind(classOf[UserDAO]).to(classOf[UserDAO])
//    bind(classOf[GroupsDAO]).to(classOf[GroupsDAO])
//    bind(classOf[UserGroupsDAO]).to(classOf[UserGroupsDAO])
  }
}