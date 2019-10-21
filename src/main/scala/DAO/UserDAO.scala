package DAO

import slick.jdbc.PostgresProfile.api._
import java.sql.Date

case class UsersRow(id: Option[Int], firstName: String, lastName: String, createdAt: Date, isActive: Boolean)

class UsersTable(tag: Tag) extends Table[UsersRow](tag, "users") {

  def id = column[Int]("id", O.PrimaryKey, O.AutoInc)

  def firstName = column[String]("first_name")

  def lastName = column[String]("last_name")

  def createdAt = column[Date]("created_at")

  def isActive = column[Boolean]("is_active")

  def * = (id.?, firstName, lastName, createdAt, isActive) <> (UsersRow.tupled, UsersRow.unapply)

}

class UserDAO {
  val allUsers = TableQuery[UsersTable]
  val defaultNumberOfUsersOnPage = 100

  def getUsers() = {
    val neededUsers = allUsers.take(defaultNumberOfUsersOnPage)
    neededUsers.result
  }

  def getUserById(userId: Int) = {
    val user = allUsers.filter(_.id === userId)
    user.result
  }

  def getUsersFromPage(pageNumber: Int, pageSize: Int) = {
    val startNumberOfNeededUsers = (pageNumber - 1) * pageSize
    val skipPages = allUsers.drop(startNumberOfNeededUsers)
    val usersFromPage = skipPages.take(pageSize)
    usersFromPage.result
  }
}


