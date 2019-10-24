package DAO

import slick.jdbc.PostgresProfile.api._
import java.sql.Date

import slick.dbio.Effect
import slick.sql.{FixedSqlAction, FixedSqlStreamingAction}

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

  def getUsers() = {
    val neededUsers = allUsers
    neededUsers.result
  }

  def getUserById(userId: Int) = {
    val user = allUsers.filter(_.id === userId)
    user.result
  }

  def getUsersFromPage(pageNumber: Int, pageSize: Int): FixedSqlStreamingAction[Seq[UsersRow], UsersRow, Effect.Read] = {
    val startNumberOfNeededUsers = (pageNumber - 1) * pageSize
    val skipPages = allUsers.drop(startNumberOfNeededUsers)
    val usersFromPage = skipPages.take(pageSize)
    usersFromPage.result
  }

  def updateUserInfoById(userId: Int, row: UsersRow): FixedSqlAction[Int, NoStream, Effect.Write] = {
    allUsers.filter(_.id === userId).update(row)
  }

  def insertUser(row: UsersRow)={
    allUsers returning allUsers += row
  }
}


