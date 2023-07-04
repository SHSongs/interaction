package som.interaction

import zio.config.typesafe.TypesafeConfigProvider
import zio._

object App extends ZIOAppDefault {

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.setConfigProvider(
      zio.ConfigProvider.fromEnv(pathDelim = "_").snakeCase.upperCase orElse
        TypesafeConfigProvider
          .fromHoconFilePath(
            "conf/application.local.conf"
          )
          .kebabCase orElse
        TypesafeConfigProvider
          .fromHoconFilePath(
            "conf/application.conf"
          )
          .kebabCase orElse
        TypesafeConfigProvider.fromResourcePath().kebabCase
    )

  override def run  =  ???
}
