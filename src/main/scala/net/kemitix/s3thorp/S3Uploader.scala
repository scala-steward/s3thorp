package net.kemitix.s3thorp

import java.io.File

import fs2.Stream
import cats.effect.IO
import net.kemitix.s3thorp.Main.putStrLn
import net.kemitix.s3thorp.awssdk.S3Client

trait S3Uploader
  extends S3Client
    with KeyGenerator {

  def performUpload(c: Config): File => Stream[IO, Unit] = {
    val remoteKey = generateKey(c) _
    file =>
      Stream.eval(for {
        _ <- putStrLn(s"uploading: $file")
        key = remoteKey(file)
        _ <- upload(file, c.bucket, key)
        _ <- putStrLn(s"uploaded: ${c.bucket}/$key")
      } yield ())
  }

}
