package net.kemitix.thorp.core

import java.io.{File, FileInputStream}
import java.security.MessageDigest

import cats.effect.IO
import net.kemitix.thorp.domain.{Logger, MD5Hash}

import scala.collection.immutable.NumericRange

object MD5HashGenerator {

  val maxBufferSize = 8048
  val defaultBuffer = new Array[Byte](maxBufferSize)

  def hex(in: Array[Byte]): String = {
    val md5 = MessageDigest getInstance "MD5"
    md5 update in
    (md5.digest map ("%02x" format _)).mkString
  }

  def digest(in: String): Array[Byte] = {
    val md5 = MessageDigest getInstance "MD5"
    md5 update in.getBytes
    md5.digest
  }

  def md5File(file: File)(implicit logger: Logger): IO[MD5Hash] =
    md5FileChunk(file, 0, file.length)

  private def openFile(file: File, offset: Long) = IO {
    val stream = new FileInputStream(file)
    stream skip offset
    stream
  }

  private def closeFile(fis: FileInputStream) = IO(fis.close())

  private def readFile(file: File, offset: Long, endOffset: Long) =
    for {
      fis <- openFile(file, offset)
      digest <- digestFile(fis, offset, endOffset)
      _ <- closeFile(fis)
    } yield digest

  private def digestFile(fis: FileInputStream, offset: Long, endOffset: Long) =
    IO {
      val md5 = MessageDigest getInstance "MD5"
      NumericRange(offset, endOffset, maxBufferSize)
        .foreach(currentOffset => md5 update readToBuffer(fis, currentOffset, endOffset))
      md5.digest
    }

  private def readToBuffer(fis: FileInputStream,
                   currentOffset: Long,
                   endOffset: Long) = {
    val buffer =
      if (nextBufferSize(currentOffset, endOffset) < maxBufferSize)
        new Array[Byte](nextBufferSize(currentOffset, endOffset))
      else defaultBuffer
    fis read buffer
    buffer
  }

  private def nextBufferSize(currentOffset: Long, endOffset: Long) = {
    val toRead = endOffset - currentOffset
    val result = Math.min(maxBufferSize, toRead)
    result.toInt
  }

  def md5FileChunk(file: File,
                   offset: Long,
                   size: Long)
                  (implicit logger: Logger): IO[MD5Hash] = {
    val endOffset = Math.min(offset + size, file.length)
    for {
      _ <- logger.debug(s"md5:reading:size ${file.length}:$file")
      digest <- readFile(file, offset, endOffset)
      hash = MD5Hash.fromDigest(digest)
      _ <- logger.debug(s"md5:generated:${hash.hash}:$file")
    } yield hash
  }

}
