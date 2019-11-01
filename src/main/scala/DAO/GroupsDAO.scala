package DAO

import java.sql.Date
import slick.jdbc.PostgresProfile.api._

case class GroupsRow(id: Option[Int], title: String, createdAt: Date, description: String)

class GroupsTable(tag: Tag) extends Table[GroupsRow](tag, Some("slick_users"),"groups") {

  def id = column[Int]("id", O.PrimaryKey, O.AutoInc)

  def title = column[String]("title")

  def createdAt = column[Date]("created_at")

  def description = column[String]("description")

  def * = (id.?, title, createdAt, description) <> (GroupsRow.tupled, GroupsRow.unapply)

}


class GroupsDAO   {
  val allGroups = TableQuery[GroupsTable]

  def getGroups() = {
    allGroups.result
  }

  def getGroupById(groupId: Int) = {
    val group = allGroups.filter(_.id === groupId)
    group.result
  }

  def getGroupsByIds(groupIds: Seq[Int]) ={
    val groups = allGroups.filter(_.id inSet groupIds)
    groups.result
  }

  def getGroupsFromPage(pageNumber: Int, pageSize: Int) = {
    val startNumberOfNeededUsers = (pageNumber - 1) * pageSize
    val skipPages = allGroups.drop(startNumberOfNeededUsers)
    val groupsFromPage = skipPages.take(pageSize)
    groupsFromPage.result
  }
}
