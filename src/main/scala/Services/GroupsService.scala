package Services

import Controller.{GroupsDTO, UserWithGroupsDTO, UsersDTO}
import DAO.{GroupsDAO, UserDAO, UserGroupsDAO}

import scala.concurrent.{ExecutionContext, Future}
import Config._
import com.google.inject.Guice

class GroupsService(userDAO: UserDAO = new UserDAO,
                   groupsDAO: GroupsDAO = new GroupsDAO,
                   userGroupsDAO: UserGroupsDAO = new UserGroupsDAO,
                   dbConfig: Db = Guice.createInjector(new GuiceModule).getInstance(classOf[Db])
                  ) {

  implicit val executionContext = ExecutionContext.global

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
}