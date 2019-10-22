package Services

import Controller.{GroupsDTO, UserWithGroupsDTO, UsersDTO}
import DAO.{GroupsDAO, UserDAO, UserGroupsDAO}

import scala.concurrent.{ExecutionContext, Future}
import Config._
import com.google.inject.Guice

class UsersService(userDAO: UserDAO = new UserDAO,
                   groupsDAO: GroupsDAO = new GroupsDAO,
                   userGroupsDAO: UserGroupsDAO = new UserGroupsDAO,
                   dbConfig: Db = Guice.createInjector(new GuiceModule).getInstance(classOf[Db])
                  ) {

  implicit val executionContext = ExecutionContext.global

  def getUsers(): Future[Seq[UsersDTO]] = {
    dbConfig.db.run(userDAO.getUsers()).map {
      usersRows =>
        usersRows.map(usersRow =>
          UsersDTO(id = usersRow.id, firstName = usersRow.firstName, lastName = usersRow.lastName, createdAt = usersRow.createdAt.toString, isActive = usersRow.isActive))
    }
  }

  def getUserById(userId: Int): Future[Option[UsersDTO]] = {
    dbConfig.db.run(userDAO.getUserById(userId)).map {
      userRows =>
        userRows.headOption match {
          case None => None
          case Some(userRow) => Some(UsersDTO(id = userRow.id, firstName = userRow.firstName, lastName = userRow.lastName, createdAt = userRow.createdAt.toString, isActive = userRow.isActive))
        }
    }
  }

  def getDetailsForUser(userId: Int): Future[Option[UserWithGroupsDTO]] = {
    val userF: Future[Option[UsersDTO]] = dbConfig.db.run(userDAO.getUserById(userId)).map {
      userRows =>
        userRows.headOption match {
          case None => None
          case Some(userRow) => Some(UsersDTO(id = userRow.id, firstName = userRow.firstName, lastName = userRow.lastName, createdAt = userRow.createdAt.toString, isActive = userRow.isActive))
        }
    }
    val groupsIdsForUserF = dbConfig.db.run(userGroupsDAO.getAllGroupsForUser(userId))
    val groupsF = groupsIdsForUserF.flatMap(groupId => dbConfig.db.run(groupsDAO.getGroupsByIds(groupId)).map {
      groupsRows =>
        groupsRows.map(groupsRow => GroupsDTO(id = groupsRow.id, title = groupsRow.title, createdAt = groupsRow.createdAt.toString, description = groupsRow.description))
    })
    val seqF = for {
      user <- userF
      groups <- groupsF
    } yield (user, groups)
    seqF.map { result =>
      val (user, groups) = result
      user match {
        case None => None
        case Some(user) => Some(UserWithGroupsDTO(user, groups))
      }
    }
  }

  def getUsersFromPage(pageSize: Int, pageNumber: Int): Future[Seq[UsersDTO]] = {
    dbConfig.db.run(userDAO.getUsersFromPage(pageNumber, pageSize)).map {
      userRows =>
        userRows.map(userRow =>
          UsersDTO(id = userRow.id, firstName = userRow.firstName, lastName = userRow.lastName, createdAt = userRow.createdAt.toString, isActive = userRow.isActive))
    }
  }
}
