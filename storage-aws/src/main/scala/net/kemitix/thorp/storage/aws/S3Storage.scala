package net.kemitix.thorp.storage.aws

import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.transfer.TransferManagerBuilder
import net.kemitix.thorp.config.Config
import net.kemitix.thorp.console.Console
import net.kemitix.thorp.domain.StorageQueueEvent.ShutdownQueueEvent
import net.kemitix.thorp.domain._
import net.kemitix.thorp.storage.api.Storage
import net.kemitix.thorp.storage.api.Storage.Service
import zio.{TaskR, UIO, ZIO}

object S3Storage {
  trait Live extends Storage {
    val storage: Service = new Service {

      private val client: AmazonS3.Client =
        AmazonS3.ClientImpl(AmazonS3ClientBuilder.defaultClient)
      private val transferManager: AmazonTransferManager =
        AmazonTransferManager(TransferManagerBuilder.defaultTransferManager)

      override def listObjects(
          bucket: Bucket,
          prefix: RemoteKey): TaskR[Console, S3ObjectsData] =
        Lister.listObjects(client)(bucket, prefix)

      override def upload(
          localFile: LocalFile,
          bucket: Bucket,
          uploadEventListener: UploadEventListener,
          tryCount: Int): ZIO[Config, Nothing, StorageQueueEvent] =
        Uploader.upload(transferManager)(localFile,
                                         bucket,
                                         uploadEventListener,
                                         1)

      override def copy(bucket: Bucket,
                        sourceKey: RemoteKey,
                        hash: MD5Hash,
                        targetKey: RemoteKey): UIO[StorageQueueEvent] =
        Copier.copy(client)(bucket, sourceKey, hash, targetKey)

      override def delete(bucket: Bucket,
                          remoteKey: RemoteKey): UIO[StorageQueueEvent] =
        Deleter.delete(client)(bucket, remoteKey)

      override def shutdown: UIO[StorageQueueEvent] = {
        transferManager.shutdownNow(true)
        client.shutdown().map(_ => ShutdownQueueEvent())
      }
    }
  }
  object Live extends Live
}