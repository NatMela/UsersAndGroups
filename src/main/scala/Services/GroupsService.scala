package Services

import Controller.{GroupWithUsersDTO, GroupsDTO, UserWithGroupsDTO, UsersDTO}
import DAO.{GroupsDAO, GroupsRow, UserDAO, UserGroupsDAO}

import scala.concurrent.{ExecutionContext, Future}
import Config._
import com.google.inject.Guice
import org.slf4j.LoggerFactory

class GroupsService(userDAO: UserDAO = new UserDAO,
                   groupsDAO: GroupsDAO = new GroupsDAO,
                   userGroupsDAO: UserGroupsDAO = new UserGroupsDAO,
                   dbConfig: Db = Guice.createInjector().getInstance(classOf[PostgresDB])
                  ) {

  implicit val executionContext = ExecutionContext.global
  lazy val log = LoggerFactory.getLogger(classOf[GroupsService])

  def getGroups: Future[Seq[GroupsDTO]] = {
    dbConfig.db().run(groupsDAO.getGroups()).map {
      groupsRows =>
        groupsRows.map(groupsRow =>
          GroupsDTO(id = groupsRow.id, title = groupsRow.title, createdAt = groupsRow.createdAt.toString, description = groupsRow.description))
    }
  }

  def getGroupsFromPage(pageSize: Int, pageNumber: Int): Future[Seq[GroupsDTO]] = {
    dbConfig.db.run(groupsDAO.getGroupsFromPage(pageNumber, pageSize)).map {
      groupsRows =>
        groupsRows.map(groupsRow =>
          GroupsDTO(id = groupsRow.id, title = groupsRow.title, createdAt = groupsRow.createdAt.toString, description = groupsRow.description))
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
            Some(GroupsDTO(id = groupRow.id, title = groupRow.title, createdAt = groupRow.createdAt.toString, description = groupRow.description))
          }
        }
    }
  }

  def getDetailsForGroup(groupId: Int): Future[Option[GroupWithUsersDTO]] = {
    val groupF: Future[Option[GroupsDTO]] = dbConfig.db.run(groupsDAO.getGroupById(groupId)).map {
      groupRows =>
        groupRows.headOption match {
          case None => None
          case Some(groupRow) => Some(GroupsDTO(id = groupRow.id, createdAt = groupRow.createdAt.toString, title = groupRow.title, description = groupRow.description))
        }
    }
    val usersIdsForGroupF = dbConfig.db.run(userGroupsDAO.getAllUsersForGroup(groupId))
    val usersF = usersIdsForGroupF.flatMap(userId => dbConfig.db.run(userDAO.getUsersByIds(userId)).map {
      userRows =>
        userRows.map(userRow => UsersDTO(id =userRow.id, firstName = userRow.firstName, lastName = userRow.lastName, createdAt = userRow.createdAt.toString, isActive = userRow.isActive))
    })
    val seqF = for {
      users <- usersF
      group <- groupF
    } yield (users, group)
    seqF.map { result =>
      val (users, group) = result
      group match {
        case None => None
        case Some(group) => Some(GroupWithUsersDTO(group, users))
      }
    }
  }

  def updateGroupById(groupId: Int, groupRow: GroupsDTO): Future[Option[GroupsDTO]] = {
    val groupF = getGroupById(groupId)
    groupF.flatMap {
      case Some(group) => {
        val rowToUpdate = GroupsRow(id = group.id, title = groupRow.title, createdAt = java.sql.Date.valueOf(groupRow.createdAt), description = groupRow.description  )
        dbConfig.db.run(groupsDAO.update(rowToUpdate)).flatMap(_ => getGroupById(groupId))
      }
      case None => Future.successful(None)
    }
  }

  def insertGroup(group: GroupsDTO): Future[Option[GroupsDTO]] = {
    val insertedGroup = GroupsRow(id = group.id, title = group.title, createdAt = java.sql.Date.valueOf(group.createdAt), description = group.description)
    val idF = dbConfig.db.run(groupsDAO.insert(insertedGroup))
    idF.flatMap { id =>
      dbConfig.db.run(groupsDAO.getGroupById(id)).map {
        groupRows =>
          groupRows.headOption match {
            case None => None
            case Some(groupRow) => Some(GroupsDTO(id = groupRow.id, title = groupRow.title, createdAt = groupRow.createdAt.toString, description = groupRow.description))
          }
      }
    }
  }

  def deleteGroup(groupId: Int): Future[Unit] = {
    getGroupById(groupId).map {
      case Some(_) => dbConfig.db().run(groupsDAO.delete(groupId))
        val message = s"Group with id $groupId is deleted"
        log.info(message)
      case None => val message = s"Group with id $groupId is not found"
        log.info(message)
    }
  }

  def deleteGroupForUser(userId: Int, groupId: Int): Future[Unit] ={
    dbConfig.db().run(userGroupsDAO.deleteRowForParticularUserAndGroup(userId, groupId))
    val message = s"User with id $userId is deleted from group with $groupId"
    log.info(message)
    Future.successful()
  }
}