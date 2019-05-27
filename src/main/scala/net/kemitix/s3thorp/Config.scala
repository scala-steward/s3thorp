package net.kemitix.s3thorp

import java.io.File

case class Config(bucket: Bucket = Bucket(""),
                  prefix: RemoteKey = RemoteKey(""),
                  verbose: Int = 1,
                  filters: Seq[Filter] = List(),
                  multiPartThreshold: Long = 1024 * 1024 * 5,
                  maxRetries: Int = 3,
                  source: File
               ) {
  require(source.isDirectory, s"Source must be a directory: $source")
  require(multiPartThreshold >= 1024 * 1024 * 5, s"Threshold for multi-part upload is 5Mb: '$multiPartThreshold'")
}
