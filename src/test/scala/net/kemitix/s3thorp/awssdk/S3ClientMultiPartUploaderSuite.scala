package net.kemitix.s3thorp.awssdk

import scala.collection.JavaConverters._
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicReference}

import com.amazonaws.services.s3.model.{Bucket => _, _}
import net.kemitix.s3thorp._

class S3ClientMultiPartUploaderSuite
  extends UnitTest
    with KeyGenerator {

  private val source = Resource(this, "..")
  private val prefix = RemoteKey("prefix")
  implicit private val config: Config = Config(Bucket("bucket"), prefix, source = source)
  private val fileToKey = generateKey(config.source, config.prefix) _

  describe("multi-part uploader accepts") {
    val uploader = new S3ClientMultiPartUploader(new MyAmazonS3Client {})

    it("should reject small file") {
      // small-file: dd if=/dev/urandom of=src/test/resources/net/kemitix/s3thorp/small-file bs=1047552 count=5
      // 1047552 = 1024 * 1023
      // file size 5kb under 5Mb threshold
      val smallFile = aLocalFile("small-file", MD5Hash(""), source, fileToKey)
      assert(smallFile.file.exists, "sample small file is missing")
      assert(smallFile.file.length == 5 * 1024 * 1023, "sample small file is wrong size")
      val result = uploader.accepts(smallFile)
      assertResult(false)(result)
    }
    it("should accept big file") {
      // big-file: dd if=/dev/urandom of=src/test/resources/net/kemitix/s3thorp/big-file bs=1049600 count=5
      // 1049600 = 1024 * 1025
      // file size 5kb over 5Mb threshold
      val bigFile = aLocalFile("big-file", MD5Hash(""), source, fileToKey)
      assert(bigFile.file.exists, "sample big file is missing")
      assert(bigFile.file.length == 5 * 1024 * 1025, "sample big file is wrong size")
      val result = uploader.accepts(bigFile)
      assertResult(true)(result)
    }
  }

  def uploadPartRequest(partNumber: Int) = {
    val request = new UploadPartRequest
    request.setPartNumber(partNumber)
    request
  }

  def uploadPartResult(eTag: String) = {
    val result = new UploadPartResult
    result.setETag(eTag)
    result
  }

  describe("mulit-part uploader upload") {
    val theFile = aLocalFile("big-file", MD5Hash(""), source, fileToKey)
    val uploadId = "upload-id"
    val createUploadResponse = new InitiateMultipartUploadResult()
    createUploadResponse.setBucketName(config.bucket.name)
    createUploadResponse.setKey(theFile.remoteKey.key)
    createUploadResponse.setUploadId(uploadId)
    val uploadPartRequest1 = uploadPartRequest(1)
    val uploadPartRequest2 = uploadPartRequest(2)
    val uploadPartRequest3 = uploadPartRequest(3)
    val part1md5 = "aadf0d266cefe0fcdb241a51798d74b3"
    val part2md5 = "16e08d53ca36e729d808fd5e4f7e35dc"
    val uploadPartResponse1 = uploadPartResult(part1md5)
    val uploadPartResponse2 = uploadPartResult(part2md5)
    val uploadPartResponse3 = uploadPartResult("part-3")
    val completeUploadResponse = new CompleteMultipartUploadResult()
    completeUploadResponse.setETag("hash")
    describe("multi-part uploader upload components") {
      val uploader = new RecordingMultiPartUploader()
      describe("create upload request") {
        val request = uploader.createUploadRequest(config.bucket, theFile)
        it("should have bucket") {
          assertResult(config.bucket.name)(request.getBucketName)
        }
        it("should have key") {
          assertResult(theFile.remoteKey.key)(request.getKey)
        }
      }
      describe("initiate upload") {
        it("should createMultipartUpload") {
          val expected = createUploadResponse
          val result = uploader.createUpload(config.bucket, theFile).unsafeRunSync
          assertResult(expected)(result)
        }
      }
      describe("create UploadPartRequests for file") {
        val chunkSize = 5l * 1024 * 1025 / 2
        // to create expected md5 values for each chunk:
        // split -d -b $((5 * 1024 * 1025 / 2)) big-file
        // creates x00 and x01
        // md5sum x0[01]
        val result = uploader.parts(theFile, createUploadResponse).unsafeRunSync.toList
        it("should create two parts") {
          assertResult(2)(result.size)
        }
        it("create part 1") {
          val part1 = result(0)
          assertResult((1, chunkSize, part1md5))((part1.getPartNumber, part1.getPartSize, part1.getMd5Digest))
        }
        it("create part 2") {
          val part2 = result(1)
          assertResult((2, chunkSize, part2md5))((part2.getPartNumber, part2.getPartSize, part2.getMd5Digest))
        }
      }
      describe("upload part") {
        it("should uploadPart") {
          val expected = uploadPartResponse3
          val result = uploader.uploadPart(theFile)(config)(uploadPartRequest3).unsafeRunSync
          assertResult(expected)(result)
        }
      }
      describe("upload parts") {
        val uploadPartRequests = Stream(uploadPartRequest1, uploadPartRequest2)
        it("should uploadPart for each") {
          val expected = List(uploadPartResponse1, uploadPartResponse2)
          val result = uploader.uploadParts(theFile, uploadPartRequests).unsafeRunSync.toList
          assertResult(expected)(result)
        }
      }
      describe("create complete request") {
        val request = uploader.createCompleteRequest(createUploadResponse, List(uploadPartResponse1, uploadPartResponse2))
        it("should have the bucket name") {
          assertResult(config.bucket.name)(request.getBucketName)
        }
        it("should have the key") {
          assertResult(theFile.remoteKey.key)(request.getKey)
        }
        it("should have the upload id") {
          assertResult(uploadId)(request.getUploadId)
        }
        it("should have the etags") {
          val expected = List(new PartETag(1, part1md5), new PartETag(2, part2md5))
          assertResult(expected.map(_.getETag))(request.getPartETags.asScala.map(_.getETag))
        }
      }
      describe("complete upload") {
        val uploadPartResponses = Stream(uploadPartResponse1, uploadPartResponse2, uploadPartResponse3)
        it("should completeUpload") {
          val expected = completeUploadResponse
          val result = uploader.completeUpload(createUploadResponse, uploadPartResponses, theFile).unsafeRunSync
          assertResult(expected)(result)
        }
      }
      describe("create abort request") {
        val abortRequest = uploader.createAbortRequest(uploadId, theFile)
        it("should have the upload id") {
          assertResult(uploadId)(abortRequest.getUploadId)
        }
        it("should have the bucket") {
          assertResult(config.bucket.name)(abortRequest.getBucketName)
        }
        it("should have the key") {
          assertResult(theFile.remoteKey.key)(abortRequest.getKey)
        }
      }
      describe("abort upload") {
        it("should abortUpload") {
          pending
        }
      }
    }
    describe("multi-part uploader upload complete") {
      describe("upload") {
        describe("when all okay") {
          val uploader = new RecordingMultiPartUploader()
          uploader.upload(theFile, config.bucket, 1).unsafeRunSync
          it("should initiate the upload") {
            assert(uploader.initiated.get)
          }
          it("should upload both parts") {
            assertResult(Set(1, 2))(uploader.partsUploaded.get)
          }
          it("should complete the upload") {
            assert(uploader.completed.get)
          }
        }
        describe("when initiate upload fails") {
          val uploader = new RecordingMultiPartUploader(initOkay = false)
          uploader.upload(theFile, config.bucket, 1).unsafeRunSync
          it("should not upload any parts") {
            assertResult(Set())(uploader.partsUploaded.get)
          }
          it("should not complete the upload") {
            assertResult(false)(uploader.completed.get)
          }
        }
        describe("when uploading a part fails once") {
          val uploader = new RecordingMultiPartUploader(partTriesRequired = 2)
          uploader.upload(theFile, config.bucket, 1).unsafeRunSync
          it("should initiate the upload") {
            assert(uploader.initiated.get)
          }
          it("should upload all parts") {
            assertResult(Set(1, 2))(uploader.partsUploaded.get)
          }
          it("should complete the upload") {
            assert(uploader.completed.get)
          }
        }
        describe("when uploading a part fails too many times") {
          val uploader = new RecordingMultiPartUploader(partTriesRequired = 4)
          uploader.upload(theFile, config.bucket, 1).unsafeRunSync
          it("should initiate the upload") {
            assert(uploader.initiated.get)
          }
          it("should not complete the upload") {
            assertResult(Set())(uploader.partsUploaded.get)
          }
          it("should cancel the upload") {
            assert(uploader.canceled.get)
          }
        }
      }
    }
    class RecordingMultiPartUploader(initOkay: Boolean = true,
                                     partTriesRequired: Int = 1,
                                     val initiated: AtomicBoolean = new AtomicBoolean(false),
                                     val partsUploaded: AtomicReference[Set[Int]] = new AtomicReference[Set[Int]](Set()),
                                     val part0Tries: AtomicInteger = new AtomicInteger(0),
                                     val part1Tries: AtomicInteger = new AtomicInteger(0),
                                     val part2Tries: AtomicInteger = new AtomicInteger(0),
                                     val completed: AtomicBoolean = new AtomicBoolean(false),
                                     val canceled: AtomicBoolean = new AtomicBoolean(false))
      extends S3ClientMultiPartUploader(
        new MyAmazonS3Client {

          def error[A]: A = {
            val exception = new AmazonS3Exception("error")
            exception.setAdditionalDetails(Map("Content-MD5" -> "(hash)").asJava)
            throw exception
          }

          override def initiateMultipartUpload(createMultipartUploadRequest: InitiateMultipartUploadRequest): InitiateMultipartUploadResult =
            if (initOkay) {
              initiated set true
              createUploadResponse
            }
            else error

          override def uploadPart(uploadPartRequest: UploadPartRequest): UploadPartResult =
            uploadPartRequest match {
              case _ if uploadPartRequest.getPartNumber == 1 => {
                if (part0Tries.incrementAndGet >= partTriesRequired) {
                  partsUploaded getAndUpdate (t => t + 1)
                  uploadPartResponse1
                }
                else error
              }
              case _ if uploadPartRequest.getPartNumber == 2 => {
                if (part1Tries.incrementAndGet >= partTriesRequired) {
                  partsUploaded getAndUpdate (t => t + 2)
                  uploadPartResponse2
                }
                else error
              }
              case _ if uploadPartRequest.getPartNumber == 3 => {
                if (part2Tries.incrementAndGet >= partTriesRequired) {
                  partsUploaded getAndUpdate (t => t + 3)
                  uploadPartResponse3
                }
                else error
              }
            }

          override def completeMultipartUpload(completeMultipartUploadRequest: CompleteMultipartUploadRequest): CompleteMultipartUploadResult = {
            completed set true
            completeUploadResponse
          }

          override def abortMultipartUpload(abortMultipartUploadRequest: AbortMultipartUploadRequest): Unit = {
            canceled set true
          }
        }) {}
  }
}