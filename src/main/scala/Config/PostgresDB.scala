package Config

import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

class PostgresDB extends Db {
  val dbconf = Database.forConfig("usersGroups")
  def db():PostgresProfile.backend.Database = {
    dbconf
  }
}
