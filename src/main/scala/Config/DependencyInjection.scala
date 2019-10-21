package Config

// GuiceModule
import com.google.inject.AbstractModule

class GuiceModule extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[Db]).to(classOf[PostgresDB])
  }
}