package som.interaction.discord

import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.interactions.commands.Command.Choice
import net.dv8tion.jda.api.interactions.commands.build.{Commands, OptionData, SlashCommandData}
import net.dv8tion.jda.api.interactions.commands.{OptionMapping, OptionType}
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.{JDA, JDABuilder}
import zio._
import zio.config.magnolia.deriveConfig
import zio.stream.ZStream

import scala.jdk.CollectionConverters._

case class DiscordAPIConfig(
    token: String,
    gatewayIntents: Seq[GatewayIntent]
)

object DiscordAPIConfig {
  val config: Config[String] =
    deriveConfig[String].nested("discord-api", "token")
}

abstract class SlashCommandOptionType[A <: Any](
    val baseOptionType: OptionType
) {
  def fromMap(mapping: OptionMapping): Option[A]
}

abstract class SlashCommandOption[A](
    val optionType: SlashCommandOptionType[A]
) {
  val name: String
  val description: String
  val isRequired: Boolean = false
  val isAutoComplete: Boolean = false
  val choices: List[Choice] = List.empty

  def fromEvent(implicit e: SlashCommandInteractionEvent): Option[A] =
    Option(e.getOption(name)).flatMap(optionType.fromMap)

  def buildData: OptionData => OptionData = identity

  def toData: OptionData =
    buildData(
      new OptionData(
        optionType.baseOptionType,
        name,
        description,
        isRequired,
        isAutoComplete
      ).addChoices(choices.asJava)
    )

}

object ExtensionJDA {
  implicit class SlashCommandOption(mapping: OptionMapping) {
    def mapAs[A](optionType: SlashCommandOptionType[A]): Option[A] =
      optionType.fromMap(mapping)
  }

  implicit class SlashCommandEvent(event: SlashCommandInteractionEvent) {
    def is(command: SlashCommand[_, _]): Boolean = command.matchedBy(event)
  }

  implicit class StringSelectEvent(
      event: StringSelectInteractionEvent
  ) {
    def is(command: StringSelect[_]): Boolean =
      command.matchedBy(event)
  }
}

trait StringSelect[R] {
  val key: String

  def matchedBy(event: StringSelectInteractionEvent): Boolean =
    event.getComponentId == key

  def task(event: StringSelectInteractionEvent): RIO[R, Unit]
}

trait SlashCommand[R, A] {
  val name: String
  val description: String
  val options: List[SlashCommandOption[_]] = List.empty

  def task(
      in: SlashCommandInteractionEvent
  ): RIO[R, Unit]

  def matchedBy(e: SlashCommandInteractionEvent): Boolean = e.getName == name

  def buildData: SlashCommandData => SlashCommandData = identity

  def toData: SlashCommandData =
    buildData(
      Commands.slash(name, description).addOptions(options.map(_.toData).asJava)
    )
}

case class DiscordClient(
    fiber: Fiber.Runtime[Nothing, Unit],
    queue: Queue[GenericEvent],
    jda: JDA
)

object DiscordClient {

  def layer[PR, PA](
      commands: Iterable[SlashCommand[_, _]],
      eventProcess: PartialFunction[GenericEvent, ZIO[PR, Throwable, PA]]
  ): ZLayer[DiscordAPIConfig & PR, Throwable, DiscordClient] = {
    ZLayer.scoped[DiscordAPIConfig & PR] {
      ZIO.acquireRelease[
        DiscordAPIConfig & PR & Scope,
        Any,
        Throwable,
        DiscordClient
      ] {
        for {
          runtime <- ZIO.runtime[Any]

          queue <- Queue.unbounded[GenericEvent]
          config <- ZIO.service[DiscordAPIConfig]
          jda = JDABuilder
            .createDefault(config.token)
            .enableIntents(config.gatewayIntents.asJava)
            .addEventListeners(new EventListener {
              override def onEvent(event: GenericEvent): Unit =
                Unsafe.unsafe(implicit unsafe =>
                  runtime.unsafe.runToFuture(queue.offer(event))
                )
            })
            .build()

          _ <- ZIO.attempt(
            jda
              .updateCommands()
              .addCommands(commands.map(_.toData).toSeq.asJava)
              .queue()
          )

          fiber <- ZStream
            .fromQueue(queue)
            .mapZIOParUnordered(
              java.lang.Runtime.getRuntime.availableProcessors
            ) { event =>
              ZIO.logInfo(event.toString) *>
                eventProcess
                  .applyOrElse(event, (_: GenericEvent) => ZIO.unit)
                  .tapErrorCause(error => ZIO.logErrorCause(error).ignore)
                  .catchAll(error => ZIO.logError(error.getMessage).ignore)
                  .catchAllDefect(error =>
                    ZIO.logErrorCause("fatal error", Cause.fail(error)).ignore
                  )
                  .unit
            }
            .runDrain
            .forkScoped
            .onInterrupt(ZIO.debug("interrupted discord service"))
            .interruptible

        } yield DiscordClient(fiber, queue, jda)
      } { discordClient =>
        (for {
          _ <- ZIO.attempt(discordClient.jda.shutdownNow())
          _ <- discordClient.queue.shutdown
          _ <- discordClient.fiber.interrupt
        } yield ()).either
      }
    }
  }

  object Collect {
    def apply[PR, PA](
        p: PartialFunction[GenericEvent, ZIO[PR, Throwable, PA]]
    ): PartialFunction[GenericEvent, ZIO[PR, Throwable, PA]] = p
  }
}
