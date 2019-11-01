package Services

import java.sql.Date
import java.text.SimpleDateFormat

import Controller.{GroupsDTO, UserWithGroupsDTO, UsersDTO}
import DAO.{GroupsDAO, UserDAO, UserGroupsDAO, UsersRow}

import scala.concurrent.{ExecutionContext, Future}
import Config._
import com.google.inject.Guice
import org.slf4j.LoggerFactory

class UsersService(userDAO: UserDAO = new UserDAO,
                   groupsDAO: GroupsDAO = new GroupsDAO,
                   userGroupsDAO: UserGroupsDAO = new UserGroupsDAO,
                   dbConfig: Db = Guice.createInjector(new GuiceModule).getInstance(classOf[Db])
                  ) {

  implicit val executionContext = ExecutionContext.global
  lazy val log = LoggerFactory.getLogger(classOf[UsersService])

  def getUsers(): Future[Seq[UsersDTO]] = {
    dbConfig.db.run(userDAO.getUsers()).map {
      usersRows =>
        usersRows.map(usersRow =>
          UsersDTO(id = usersRow.id, firstName = usersRow.firstName, lastName = usersRow.lastName, createdAt = usersRow.createdAt.toString, isActive = usersRow.isActive))
    }
  }

  def getUserById(userId: Int): Future[Option[UsersDTO]] = {
    log.info(" We are here")
    if (userId > 0) {
      log.info(" Id > 0")
      dbConfig.db.run(userDAO.getUserById(userId)).map {
        userRows =>
          userRows.headOption match {
            case None => {
              log.info("There is no user with id {}", userId)
              None
            }
            case Some(userRow) => {
              log.info("We have user with id {}", userId)
              Some(UsersDTO(id = userRow.id, firstName = userRow.firstName, lastName = userRow.lastName, createdAt = userRow.createdAt.toString, isActive = userRow.isActive))
            }
          }
      }
    } else {
      log.info("Incorrect request: id should be > 0")
      Future.successful(None)
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

  def updateUserById(userId: Int, userRow: UsersDTO): Future[Option[UsersDTO]] = {
    val user = getUserById(userId)
    user.flatMap {
      case Some(user) => {
        val rowToUpdate = UsersRow(id = user.id, createdAt = java.sql.Date.valueOf(userRow.createdAt), firstName = userRow.firstName, lastName = userRow.lastName, isActive = userRow.isActive)
        dbConfig.db.run(userDAO.update(rowToUpdate)).flatMap(_ => getUserById(userId))
      }
      case None => Future.successful(None)
    }
  }

  def insertUser(user: UsersDTO) = {
    val insertedUser = UsersRow(id = user.id, firstName = user.firstName, lastName = user.lastName, isActive = user.isActive, createdAt = java.sql.Date.valueOf(user.createdAt))
    val idF = dbConfig.db.run(userDAO.insert(insertedUser))
    val userF: Future[Option[UsersDTO]] = idF.flatMap { id =>
      dbConfig.db.run(userDAO.getUserById(id)).map {
        userRows =>
          userRows.headOption match {
            case None => None
            case Some(userRow) => Some(UsersDTO(id = userRow.id, firstName = userRow.firstName, lastName = userRow.lastName, createdAt = userRow.createdAt.toString, isActive = userRow.isActive))
          }
      }
    }
    userF
  }


}
