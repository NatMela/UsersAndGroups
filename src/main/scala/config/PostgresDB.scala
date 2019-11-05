package config

import javax.inject.Inject
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

class PostgresDB @Inject() extends Db {
  val dbconf = Database.forConfig("usersGroups")
  def db():PostgresProfile.backend.Database = {
    dbconf
  }
}
