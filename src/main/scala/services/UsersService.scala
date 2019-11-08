package services

import controller.{GroupsDTO, UserWithGroupsDTO, UsersDTO, UsersFromPage}
import dao.{GroupsDAO, UserDAO, UserGroupsDAO, UsersAndGroupsRow, UsersRow}

import scala.concurrent.{ExecutionContext, Future}
import config._
import com.google.inject.{Guice, Inject, Singleton}
import org.slf4j.LoggerFactory
import slick.jdbc.PostgresProfile.api._

class UsersService(userDAO: UserDAO = new UserDAO,
                   groupsDAO: GroupsDAO = new GroupsDAO,
                   userGroupsDAO: UserGroupsDAO = new UserGroupsDAO,
                   dbConfig: Db = Guice.createInjector().getInstance(classOf[PostgresDB])
                  ) (implicit executionContext: ExecutionContext = ExecutionContext.global) {

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
            log.warn("There is no user with id {}", userId)
            None
          }
          case Some(userRow) => {
            log.info("User with id {} was found", userId)
            Some(UsersDTO(id = userRow.id, firstName = userRow.firstName, lastName = userRow.lastName, createdAt = userRow.createdAt.toString, isActive = userRow.isActive))
          }
        }
    }
  }

  def getDetailsForUser(userId: Int): Future[Option[UserWithGroupsDTO]] = {
    val userF: Future[Option[UsersDTO]] = dbConfig.db.run(userDAO.getUserById(userId)).map {
      userRows =>
        userRows.headOption match {
          case None =>
            log.info("There is no user with id {}", userId)
            None
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
        case None =>
          log.warn("Can't get details about user as there is no user with id {} ", userId)
          None
        case Some(user) => {
          log.info("Details for user with id {} were found", userId)
          Some(UserWithGroupsDTO(user, groups))
        }
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
    val seqF = for {
      numberOfAllUsers <- numberOfAllUsersF
      usersOnPage <- usersOnPageF
    } yield (numberOfAllUsers, usersOnPage)
    seqF.map { result =>
      val (numberOfAllUsers, usersOnPage) = result
      UsersFromPage(usersOnPage, numberOfAllUsers, usersOnPage.size)
    }
  }

  def updateUserById(userId: Int, userRow: UsersDTO): Future[Option[UsersDTO]] = {
    val user = getUserById(userId)
    user.flatMap {
      case Some(user) => {
        log.info("User with id {} was found", userId)
        val rowToUpdate = UsersRow(id = user.id, createdAt = java.sql.Date.valueOf(userRow.createdAt), firstName = userRow.firstName, lastName = userRow.lastName, isActive = userRow.isActive)
        dbConfig.db.run(userDAO.update(rowToUpdate)).flatMap(_ => getUserById(userId))
      }
      case None => {
        log.warn("Can't update info about user as there is no user with id {} ", userId)
        Future.successful(None)
      }
    }
  }

  def insertUser(user: UsersDTO) = {
    val insertedUser = UsersRow(id = user.id, firstName = user.firstName, lastName = user.lastName, isActive = user.isActive, createdAt = java.sql.Date.valueOf(user.createdAt))
    val idF = dbConfig.db.run(userDAO.insert(insertedUser))
    idF.flatMap { id =>
      dbConfig.db.run(userDAO.getUserById(id)).map {
        userRows =>
          userRows.headOption match {
            case None => {
              log.warn("User was not added ")
              None
            }
            case Some(userRow) => {
              log.info("User with id {} was created", userRow.id)
              Some(UsersDTO(id = userRow.id, firstName = userRow.firstName, lastName = userRow.lastName, createdAt = userRow.createdAt.toString, isActive = userRow.isActive))
            }
          }
      }
    }
  }

  def isUserAlreadyInGroup(userId: Int, groupId: Int) = {
    val userGroupRowF = dbConfig.db.run(userGroupsDAO.getUserGroupRow(userId, groupId))
    userGroupRowF.map(userGroupRow =>
      if (userGroupRow.nonEmpty) true else false)
  }

  def couldWeAddGroupForUser(userId: Int) = {
    val groupsForUserF = dbConfig.db.run(userGroupsDAO.getAllGroupsForUser(userId))
    groupsForUserF.map(groupsForUser =>
      if (groupsForUser.size < 16) true else false)
  }

  def needToAddUserToGroup(userId: Int, groupId: Int) = {
    val seqF = for {
      isUserInGroup <- isUserAlreadyInGroup(userId, groupId)
      couldWeAddGroup <- couldWeAddGroupForUser(userId)
    } yield (isUserInGroup, couldWeAddGroup)
    seqF.map { result =>
      val (isUserInGroup, couldWeAddGroup) = result
      if (!isUserInGroup && couldWeAddGroup)
        true
      else
        false
    }
  }

  def addUserToGroup(userId: Int, groupId: Int): Future[String] = {
    val userF = getUserById(userId)
    dbConfig.db.run(groupsDAO.getGroupById(groupId)).flatMap(groupRows =>
      groupRows.headOption match {
        case Some(_) => {
          userF.flatMap {
            case Some(user) => {
              if (user.isActive) {
                needToAddUserToGroup(userId, groupId).flatMap { needToAdd =>
                  if (needToAdd) {
                    log.info("Add user with id {} to group with id {}", userId, groupId)
                    val rowToInsert = UsersAndGroupsRow(None, userId, groupId)
                    dbConfig.db.run(userGroupsDAO.insert(rowToInsert))
                    Future.successful(s"")
                  } else {
                    log.warn("Don't add user to group as user with id {} is already in group with id {} or user is included for 16 groups", userId, groupId)
                    Future.successful(s"Don't add user to group as user with id $userId is already in group with id $groupId or user is already included in 16 groups")
                  }
                }
              } else {
                log.warn("Don't add user to group as user with id {} is is nonActive", userId)
                Future.successful(s"Don't add user to group as user with id $userId is nonActive")
              }
            }
            case None => {
              log.warn("Don't add user to group as user with id {} is not exist", userId)
              Future.successful(s"Don't add user to group as user with id $userId is not exist")
            }
          }
        }
        case None => {
          log.warn("Don't add user to group as group with id {} is not exist", groupId)
          Future.successful(s"Don't add user to group as group with id $groupId is not exist")
        }
      })
  }

  def deleteUser(userId: Int): Future[Unit] = {
    getUserById(userId).map {
      case Some(_) => dbConfig.db().run(userDAO.delete(userId).transactionally)
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
        log.info("Set user with id {} as active", userId)
        val rowToUpdate = UsersDTO(id = Some(userId), firstName = user.firstName, lastName = user.lastName, createdAt = user.createdAt, isActive = true)
        updateUserById(userId, rowToUpdate)
      }
      case None => {
        log.warn("Can't make user active because can't find user with id {} ", userId)
        Future.successful(None)
      }
    }
  }

  def setUserAsNonActive(userId: Int) = {
    val userF = getUserById(userId)
    userF.flatMap {
      case Some(user) => {
        log.info("Set user with id {} as nonActive", userId)
        val rowToUpdate = UsersDTO(id = Some(userId), firstName = user.firstName, lastName = user.lastName, createdAt = user.createdAt, isActive = false)
        dbConfig.db.run(userGroupsDAO.deleteGroupsForUser(userId))
        updateUserById(userId, rowToUpdate)
      }
      case None => {
        log.warn("Can't make user nonActive as can't find user with id {} ", userId)
        Future.successful(None)
      }
    }
  }

  def deleteUserFromGroup(userId: Int, groupId: Int): Future[Unit] = {
    dbConfig.db().run(userGroupsDAO.deleteRowForParticularUserAndGroup(userId, groupId))
    val message = s"User with id $userId is deleted from group with $groupId"
    log.info(message)
    Future.successful()
  }
}
