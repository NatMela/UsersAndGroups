package services

import java.time.LocalDate

import controller.{GroupWithUsersDTO, GroupsDTO, GroupsFromPage, UsersDTO}
import dao.{GroupsDAO, GroupsRow, UserDAO, UserGroupsDAO, UsersAndGroupsRow}

import scala.concurrent.{ExecutionContext, Future}
import config._
import com.google.inject.Guice
import org.slf4j.LoggerFactory
import slick.jdbc.PostgresProfile.api._


class GroupsService(userDAO: UserDAO = new UserDAO,
                    groupsDAO: GroupsDAO = new GroupsDAO,
                    userGroupsDAO: UserGroupsDAO = new UserGroupsDAO,
                    dbConfig: Db = Guice.createInjector().getInstance(classOf[PostgresDB])
                   ) (implicit executionContext: ExecutionContext = ExecutionContext.global) {

  lazy val log = LoggerFactory.getLogger(classOf[GroupsService])

  val maxGroupNumber = 16

  def getGroups: Future[Seq[GroupsDTO]] = {
    dbConfig.db().run(groupsDAO.getGroups()).map {
      groupsRows =>
        groupsRows.map(groupsRow =>
          GroupsDTO(id = groupsRow.id, title = groupsRow.title, createdAt = Some(groupsRow.createdAt.toString), description = groupsRow.description))
    }
  }

  def getGroupsFromPage(pageSize: Int, pageNumber: Int): Future[GroupsFromPage] = {
    val numberOfAllGroupsF = getGroups.map(allGroups => allGroups.size)
    val groupsOnPageF = dbConfig.db.run(groupsDAO.getGroupsFromPage(pageNumber, pageSize)).map {
      groupRows =>
        groupRows.map(groupRow =>
          GroupsDTO(id = groupRow.id, title = groupRow.title, createdAt = Some(groupRow.createdAt.toString), description = groupRow.description))
    }
    val seqF = for {
      numberOfAllGroups <- numberOfAllGroupsF
      groupsOnPage <- groupsOnPageF
    } yield (numberOfAllGroups, groupsOnPage)
    seqF.map { result =>
      val (numberOfAllGroups, groupsOnPage) = result
      GroupsFromPage(groupsOnPage, numberOfAllGroups, pageNumber, pageSize)
    }
  }

  def getGroupById(groupId: Int): Future[Option[GroupsDTO]] = {
    dbConfig.db.run(groupsDAO.getGroupById(groupId)).map {
      groupRows =>
        groupRows.headOption match {
          case None => {
            log.info("There is no group with id {}", groupRows)
            None
          }
          case Some(groupRow) => {
            log.info("Group with id {} was found", groupId)
            Some(GroupsDTO(id = groupRow.id, title = groupRow.title, createdAt = Some(groupRow.createdAt.toString), description = groupRow.description))
          }
        }
    }
  }

  def getDetailsForGroup(groupId: Int): Future[Option[GroupWithUsersDTO]] = {
    val groupF: Future[Option[GroupsDTO]] = dbConfig.db.run(groupsDAO.getGroupById(groupId)).map {
      groupRows =>
        groupRows.headOption match {
          case None =>
            log.info("There is no group with id {}", groupId)
            None
          case Some(groupRow) => Some(GroupsDTO(id = groupRow.id, createdAt = Some(groupRow.createdAt.toString), title = groupRow.title, description = groupRow.description))
        }
    }
    val usersIdsForGroupF = dbConfig.db.run(userGroupsDAO.getAllUsersForGroup(groupId))
    val usersF = usersIdsForGroupF.flatMap(userId => dbConfig.db.run(userDAO.getUsersByIds(userId)).map {
      userRows =>
        userRows.map(userRow => UsersDTO(id = userRow.id, firstName = userRow.firstName, lastName = userRow.lastName, createdAt = Some(userRow.createdAt.toString), isActive = userRow.isActive))
    })
    val seqF = for {
      users <- usersF
      group <- groupF
    } yield (users, group)
    seqF.map { result =>
      val (users, group) = result
      group match {
        case None =>
          log.warn("Can't ge details about the group as there is no group with id {}", groupId)
          None
        case Some(group) => {
          log.info("Details for group with id {} were found", groupId)
          Some(GroupWithUsersDTO(group, users))
        }
      }
    }
  }

  def updateGroupById(groupId: Int, groupRow: GroupsDTO): Future[Option[GroupsDTO]] = {
    val groupF = getGroupById(groupId)
    groupF.flatMap {
      case Some(group) => {
        log.info("Group with id {} was updated", groupId)
        val rowToUpdate = GroupsRow(id = group.id, title = groupRow.title, createdAt = java.sql.Date.valueOf(groupRow.createdAt.get), description = groupRow.description)
        dbConfig.db.run(groupsDAO.update(rowToUpdate)).flatMap(_ => getGroupById(groupId))
      }
      case None => {
        log.warn("Can't update group info, because there is no group with id {}", groupId)
        Future.successful(None)
      }
    }
  }

  def insertGroup(group: GroupsDTO): Future[Option[GroupsDTO]] = {
    val insertedGroup = GroupsRow(id = group.id, title = group.title, createdAt =  java.sql.Date.valueOf(LocalDate.now), description = group.description)
    val idF = dbConfig.db.run(groupsDAO.insert(insertedGroup))
    idF.flatMap { id =>
      dbConfig.db.run(groupsDAO.getGroupById(id)).map {
        groupRows =>
          groupRows.headOption match {
            case None =>
              log.warn("Group was not added")
              None
            case Some(groupRow) => {
              log.info("Group with id {} was created", groupRow.id)
              Some(GroupsDTO(id = groupRow.id, title = groupRow.title, createdAt = Some(groupRow.createdAt.toString), description = groupRow.description))
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
      if (groupsForUser.size < maxGroupNumber) true else false)
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

  def addGroupToUser(userId: Int, groupId: Int): Future[String] = {
    val groupF = getGroupById(groupId)
    dbConfig.db.run(userDAO.getUserById(userId)).flatMap(userRows =>
      userRows.headOption match {
        case Some(user) => {
          groupF.flatMap {
            case Some(_) => {
              if (user.isActive) {
                needToAddUserToGroup(userId, groupId).flatMap { needToAdd =>
                  if (needToAdd) {
                    val rowToInsert = UsersAndGroupsRow(None, userId, groupId)
                    log.info("Add user with id {} to group with id {} ", userId, groupId)
                    dbConfig.db.run(userGroupsDAO.insert(rowToInsert))
                    Future.successful("")
                  } else {
                    log.warn(s"Group was not added because user is already in group or user have already included in $maxGroupNumber groups")
                    Future.successful(s"Group was not added because user with id $userId is already in group with id $groupId or user have already included in $maxGroupNumber groups")
                  }
                }
              } else {
                log.warn("Group was not added because user is nonActive")
                Future.successful(s"Group was not added because user with id $userId is nonActive")
              }
            }
            case None => {
              log.warn("Group was not added because there is no group with id {}", groupId)
              Future.successful(s"Group was not added because there is no group with id $groupId")
            }
          }
        }
        case None => {
          log.warn("Group was not added because there is no user with id {}", userId)
          Future.successful(s"Group was not added because there is no user with id $userId")
        }
      })
  }

  def deleteGroup(groupId: Int): Future[Unit] = {
    getGroupById(groupId).map {
      case Some(_) => val query = DBIO.seq(groupsDAO.delete(groupId),
        userGroupsDAO.deleteUsersFromGroup(groupId)).transactionally
        dbConfig.db().run(query)
        val message = s"Group with id $groupId is deleted"
        log.info(message)
      case None => val message = s"Group with id $groupId is not found"
        log.info(message)
    }
  }

  def deleteGroupForUser(userId: Int, groupId: Int): Future[Unit] = {
    dbConfig.db().run(userGroupsDAO.deleteRowForParticularUserAndGroup(userId, groupId))
    val message = s"User with id $userId is deleted from group with $groupId"
    log.info(message)
    Future.successful()
  }
}