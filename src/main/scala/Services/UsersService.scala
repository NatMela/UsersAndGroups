package Services

import Controller.{GroupsDTO, UserWithGroupsDTO, UsersDTO, UsersFromPage}
import DAO.{GroupsDAO, UserDAO, UserGroupsDAO, UsersAndGroupsRow, UsersRow}

import scala.concurrent.{ExecutionContext, Future}
import Config._
import com.google.inject.{Guice, Inject, Singleton}
import org.slf4j.LoggerFactory


class UsersService(userDAO: UserDAO = new UserDAO,
                   groupsDAO: GroupsDAO = new GroupsDAO,
                   userGroupsDAO: UserGroupsDAO = new UserGroupsDAO,
                   dbConfig: Db = Guice.createInjector().getInstance(classOf[PostgresDB])
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
    dbConfig.db.run(userDAO.getUserById(userId)).map {
      userRows =>
        userRows.headOption match {
          case None => {
            log.info("There is no user with id {}", userId)
            None
          }
          case Some(userRow) => {
            Some(UsersDTO(id = userRow.id, firstName = userRow.firstName, lastName = userRow.lastName, createdAt = userRow.createdAt.toString, isActive = userRow.isActive))
          }
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

  def getUsersFromPage(pageSize: Int, pageNumber: Int): Future[UsersFromPage] = {
    val numberOfAllUsersF = getUsers().map(allUsers => allUsers.size)
    val usersOnPageF = dbConfig.db.run(userDAO.getUsersFromPage(pageNumber, pageSize)).map {
      userRows =>
        userRows.map(userRow =>
          UsersDTO(id = userRow.id, firstName = userRow.firstName, lastName = userRow.lastName, createdAt = userRow.createdAt.toString, isActive = userRow.isActive))
    }
    val seqF = for{
      numberOfAllUsers <- numberOfAllUsersF
      usersOnPage <- usersOnPageF
    } yield (numberOfAllUsers, usersOnPage)
    seqF.map{result =>
      val (numberOfAllUsers, usersOnPage) = result
      UsersFromPage(usersOnPage, numberOfAllUsers, usersOnPage.size)
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
    idF.flatMap { id =>
      dbConfig.db.run(userDAO.getUserById(id)).map {
        userRows =>
          userRows.headOption match {
            case None => None
            case Some(userRow) => Some(UsersDTO(id = userRow.id, firstName = userRow.firstName, lastName = userRow.lastName, createdAt = userRow.createdAt.toString, isActive = userRow.isActive))
          }
      }
    }
  }

//TODO if user already in this group - do nothing
  //TODO max number of groups for user
  def addUserToGroup(userId: Int, groupId: Int): Future[Int] = {
    val userF = getUserById(userId)
    dbConfig.db.run(groupsDAO.getGroupById(groupId)).flatMap(groupRows =>
      groupRows.headOption match {
        case Some(_) => {
          userF.flatMap {
            case Some(user) => {
              if (user.isActive) {
                val rowToInsert = UsersAndGroupsRow(None, userId, groupId)
                dbConfig.db.run(userGroupsDAO.insert(rowToInsert))
              } else {
                Future.successful(0)
              }
            }
            case None => Future.successful(0)
          }
        }
        case None => Future.successful(0)
      })
  }

  def deleteUser(userId: Int): Future[Unit] = {
    getUserById(userId).map {
      case Some(_) => dbConfig.db().run(userDAO.delete(userId))
        dbConfig.db.run(userGroupsDAO.deleteGroupsForUser(userId))
        val message = s"User with id $userId is deleted"
        log.info(message)
      case None => val message = s"User with id $userId is not found"
        log.info(message)
    }
  }

  def setUserAsActive(userId: Int) = {
    val userF = getUserById(userId)
    userF.flatMap {
      case Some(user) => {
        val rowToUpdate = UsersDTO(id = Some(userId), firstName = user.firstName, lastName = user.lastName, createdAt = user.createdAt, isActive = true)
        updateUserById(userId, rowToUpdate)
      }
      case None => Future.successful(None)
    }
  }

  def setUserAsNonActive(userId: Int) = {
    val userF = getUserById(userId)
    userF.flatMap {
      case Some(user) => {
        val rowToUpdate = UsersDTO(id = Some(userId), firstName = user.firstName, lastName = user.lastName, createdAt = user.createdAt, isActive = false)
        dbConfig.db.run(userGroupsDAO.deleteGroupsForUser(userId))
        updateUserById(userId, rowToUpdate)
      }
      case None => Future.successful(None)
    }
  }

  def deleteUserFromGroup(userId: Int, groupId: Int): Future[Unit] = {
    dbConfig.db().run(userGroupsDAO.deleteRowForParticularUserAndGroup(userId, groupId))
    val message = s"User with id $userId is deleted from group with $groupId"
    log.info(message)
    Future.successful()
  }
}
