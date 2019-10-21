package Config

import slick.jdbc.PostgresProfile

abstract class Db {
  def db():PostgresProfile.backend.Database
//  def getDb()
}
