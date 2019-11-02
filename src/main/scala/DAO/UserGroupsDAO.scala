package DAO

import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext
import DAO.GroupsDAO
import javax.inject.Inject

case class UsersAndGroupsRow(userGroupId: Option[Int], userId: Int, groupId: Int)

class UsersAndGroupsTable(tag: Tag) extends Table[UsersAndGroupsRow](tag, Some("slick_users"), "users_and_groups") {

  def userGroupId = column[Int]("user_group_id", O.PrimaryKey, O.AutoInc)

  def userId = column[Int]("user_id")

  def groupId = column[Int]("group_id")

  def * = (userGroupId.?, userId, groupId) <> (UsersAndGroupsRow.tupled, UsersAndGroupsRow.unapply)

}


class UserGroupsDAO   {
  implicit val executionContext = ExecutionContext.global
  val allRows = TableQuery[UsersAndGroupsTable]
  val userRows = TableQuery[UsersTable]
  val groupsRows = TableQuery[GroupsTable]
  val groupsDao = new GroupsDAO

  def getAllGroupsForUser(userId: Int) = {
    allRows.filter(_.userId === userId).map(_.groupId).result
  }

  def getAllUsersForGroup(groupId: Int) ={
    allRows.filter(_.groupId === groupId).map(_.userId).result
  }

  def deleteRowForParticularUserAndGroup(userId: Int, groupId: Int) ={
    allRows.filter(_.userId === userId).filter(_.groupId === groupId).delete
  }

  def getRowForUserAndGroup(userId: Int, groupId: Int, userGroupRow: UsersAndGroupsRow) ={
    allRows.filter(_.userId === userId).filter(_.groupId === groupId).result
  }

//TODO transactions
  def getGroupsForUsers(userId: Int) ={
    val query = (for {
      groupsId <- allRows.filter(_.userId === userId).map(_.groupId).result
      _ <- DBIO.seq(groupsId.map(groupId => groupsRows.filter(_.id === groupId).result):_*)
    }yield()).transactionally
    query
  }
}
