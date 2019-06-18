package net.kemitix.thorp.cli

import cats.Monad
import cats.effect.ExitCode
import cats.implicits._
import net.kemitix.thorp.aws.lib.S3ClientBuilder
import net.kemitix.thorp.core.Sync
import net.kemitix.thorp.domain.{Config, Logger}

object Program {

  def apply[M[_]: Monad](config: Config): M[ExitCode] = {
    implicit val logger: Logger[M] = new PrintLogger[M](config.debug)
    for {
      _ <- logger.info("Thorp - hashed sync for cloud storage")
      _ <- Sync.run[M](config, S3ClientBuilder.defaultClient)
    } yield ExitCode.Success
  }

}