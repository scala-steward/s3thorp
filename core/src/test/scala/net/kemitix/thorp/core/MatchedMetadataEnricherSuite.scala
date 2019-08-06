package net.kemitix.thorp.core

import net.kemitix.thorp.config.Resource
import net.kemitix.thorp.core.S3MetaDataEnricher.{getMetadata, getS3Status}
import net.kemitix.thorp.domain.HashType.MD5
import net.kemitix.thorp.domain._
import org.scalatest.FunSpec

class MatchedMetadataEnricherSuite extends FunSpec {
  private val source     = Resource(this, "upload")
  private val sourcePath = source.toPath
  private val sources    = Sources(List(sourcePath))
  private val prefix     = RemoteKey("prefix")

  def getMatchesByKey(
      status: (Option[MD5Hash], Set[(RemoteKey, MD5Hash)])): Option[MD5Hash] = {
    val (byKey, _) = status
    byKey
  }

  def getMatchesByHash(status: (Option[MD5Hash], Set[(RemoteKey, MD5Hash)]))
    : Set[(RemoteKey, MD5Hash)] = {
    val (_, byHash) = status
    byHash
  }

  describe("enrich with metadata") {

    describe(
      "#1a local exists, remote exists, remote matches, other matches - do nothing") {
      val theHash: MD5Hash = MD5Hash("the-file-hash")
      val env = for {
        theFile <- LocalFileValidator.resolve("the-file",
                                              md5HashMap(theHash),
                                              sourcePath,
                                              sources,
                                              prefix)
        theRemoteKey = theFile.remoteKey
        remoteObjects = RemoteObjects(
          byHash = Map(theHash     -> Set(theRemoteKey)),
          byKey = Map(theRemoteKey -> theHash)
        )
        theRemoteMetadata = RemoteMetaData(theRemoteKey, theHash)
      } yield (theFile, theRemoteMetadata, remoteObjects)
      it("generates valid metadata") {
        env.map({
          case (theFile, theRemoteMetadata, remoteObjects) => {
            val expected = MatchedMetadata(theFile,
                                           matchByHash = Set(theRemoteMetadata),
                                           matchByKey = Some(theRemoteMetadata))
            val result = getMetadata(theFile, remoteObjects)
            assertResult(expected)(result)
          }
        })
      }
    }
    describe(
      "#1b local exists, remote exists, remote matches, other no matches - do nothing") {
      val theHash: MD5Hash = MD5Hash("the-file-hash")
      val env = for {
        theFile <- LocalFileValidator.resolve("the-file",
                                              md5HashMap(theHash),
                                              sourcePath,
                                              sources,
                                              prefix)
        theRemoteKey: RemoteKey = RemoteKey.resolve("the-file")(prefix)
        remoteObjects = RemoteObjects(
          byHash = Map(theHash     -> Set(theRemoteKey)),
          byKey = Map(theRemoteKey -> theHash)
        )
        theRemoteMetadata = RemoteMetaData(theRemoteKey, theHash)
      } yield (theFile, theRemoteMetadata, remoteObjects)
      it("generates valid metadata") {
        env.map({
          case (theFile, theRemoteMetadata, remoteObjects) => {
            val expected = MatchedMetadata(theFile,
                                           matchByHash = Set(theRemoteMetadata),
                                           matchByKey = Some(theRemoteMetadata))
            val result = getMetadata(theFile, remoteObjects)
            assertResult(expected)(result)
          }
        })
      }
    }
    describe(
      "#2 local exists, remote is missing, remote no match, other matches - copy") {
      val theHash = MD5Hash("the-hash")
      val env = for {
        theFile <- LocalFileValidator.resolve("the-file",
                                              md5HashMap(theHash),
                                              sourcePath,
                                              sources,
                                              prefix)
        otherRemoteKey = RemoteKey("other-key")
        remoteObjects = RemoteObjects(
          byHash = Map(theHash       -> Set(otherRemoteKey)),
          byKey = Map(otherRemoteKey -> theHash)
        )
        otherRemoteMetadata = RemoteMetaData(otherRemoteKey, theHash)
      } yield (theFile, otherRemoteMetadata, remoteObjects)
      it("generates valid metadata") {
        env.map({
          case (theFile, otherRemoteMetadata, remoteObjects) => {
            val expected = MatchedMetadata(theFile,
                                           matchByHash =
                                             Set(otherRemoteMetadata),
                                           matchByKey = None)
            val result = getMetadata(theFile, remoteObjects)
            assertResult(expected)(result)
          }
        })
      }
    }
    describe(
      "#3 local exists, remote is missing, remote no match, other no matches - upload") {
      val theHash = MD5Hash("the-hash")
      val env = for {
        theFile <- LocalFileValidator.resolve("the-file",
                                              md5HashMap(theHash),
                                              sourcePath,
                                              sources,
                                              prefix)
        remoteObjects = RemoteObjects.empty
      } yield (theFile, remoteObjects)
      it("generates valid metadata") {
        env.map({
          case (theFile, remoteObjects) => {
            val expected =
              MatchedMetadata(theFile,
                              matchByHash = Set.empty,
                              matchByKey = None)
            val result = getMetadata(theFile, remoteObjects)
            assertResult(expected)(result)
          }
        })
      }
    }
    describe(
      "#4 local exists, remote exists, remote no match, other matches - copy") {
      val theHash = MD5Hash("the-hash")
      val env = for {
        theFile <- LocalFileValidator.resolve("the-file",
                                              md5HashMap(theHash),
                                              sourcePath,
                                              sources,
                                              prefix)
        theRemoteKey   = theFile.remoteKey
        oldHash        = MD5Hash("old-hash")
        otherRemoteKey = RemoteKey.resolve("other-key")(prefix)
        remoteObjects = RemoteObjects(
          byHash =
            Map(oldHash -> Set(theRemoteKey), theHash -> Set(otherRemoteKey)),
          byKey = Map(
            theRemoteKey   -> oldHash,
            otherRemoteKey -> theHash
          )
        )
        theRemoteMetadata   = RemoteMetaData(theRemoteKey, oldHash)
        otherRemoteMetadata = RemoteMetaData(otherRemoteKey, theHash)
      } yield (theFile, theRemoteMetadata, otherRemoteMetadata, remoteObjects)
      it("generates valid metadata") {
        env.map({
          case (theFile,
                theRemoteMetadata,
                otherRemoteMetadata,
                remoteObjects) => {
            val expected = MatchedMetadata(theFile,
                                           matchByHash =
                                             Set(otherRemoteMetadata),
                                           matchByKey = Some(theRemoteMetadata))
            val result = getMetadata(theFile, remoteObjects)
            assertResult(expected)(result)
          }
        })
      }
    }
    describe(
      "#5 local exists, remote exists, remote no match, other no matches - upload") {
      val theHash = MD5Hash("the-hash")
      val env = for {
        theFile <- LocalFileValidator.resolve("the-file",
                                              md5HashMap(theHash),
                                              sourcePath,
                                              sources,
                                              prefix)
        theRemoteKey = theFile.remoteKey
        oldHash      = MD5Hash("old-hash")
        remoteObjects = RemoteObjects(
          byHash = Map(oldHash     -> Set(theRemoteKey), theHash -> Set.empty),
          byKey = Map(theRemoteKey -> oldHash)
        )
        theRemoteMetadata = RemoteMetaData(theRemoteKey, oldHash)
      } yield (theFile, theRemoteMetadata, remoteObjects)
      it("generates valid metadata") {
        env.map({
          case (theFile, theRemoteMetadata, remoteObjects) => {
            val expected = MatchedMetadata(theFile,
                                           matchByHash = Set.empty,
                                           matchByKey = Some(theRemoteMetadata))
            val result = getMetadata(theFile, remoteObjects)
            assertResult(expected)(result)
          }
        })
      }
    }
  }

  private def md5HashMap(theHash: MD5Hash): Map[HashType, MD5Hash] = {
    Map(MD5 -> theHash)
  }

  describe("getS3Status") {
    val hash = MD5Hash("hash")
    val env = for {
      localFile <- LocalFileValidator.resolve("the-file",
                                              md5HashMap(hash),
                                              sourcePath,
                                              sources,
                                              prefix)
      key = localFile.remoteKey
      keyOtherKey <- LocalFileValidator.resolve("other-key-same-hash",
                                                md5HashMap(hash),
                                                sourcePath,
                                                sources,
                                                prefix)
      diffHash = MD5Hash("diff")
      keyDiffHash <- LocalFileValidator.resolve("other-key-diff-hash",
                                                md5HashMap(diffHash),
                                                sourcePath,
                                                sources,
                                                prefix)
      remoteObjects = RemoteObjects(
        byHash = Map(
          hash     -> Set(key, keyOtherKey.remoteKey),
          diffHash -> Set(keyDiffHash.remoteKey)
        ),
        byKey = Map(
          key                   -> hash,
          keyOtherKey.remoteKey -> hash,
          keyDiffHash.remoteKey -> diffHash
        )
      )
    } yield (remoteObjects, localFile, keyDiffHash, diffHash)

    def invoke(localFile: LocalFile, s3ObjectsData: RemoteObjects) = {
      getS3Status(localFile, s3ObjectsData)
    }

    describe("when remote key exists") {
      it("should return a result for matching key") {
        env.map({
          case (remoteObjects, localFile: LocalFile, _, _) =>
            val result = getMatchesByKey(invoke(localFile, remoteObjects))
            assert(result.contains(hash))
        })
      }
    }

    describe("when remote key does not exist and no others matches hash") {
      val env2 = for {
        localFile <- LocalFileValidator.resolve("missing-remote",
                                                md5HashMap(MD5Hash("unique")),
                                                sourcePath,
                                                sources,
                                                prefix)
      } yield (localFile)
      it("should return no matches by key") {
        env.map({
          case (remoteObjects, _, _, _) => {
            env2.map({
              case (localFile) => {
                val result = getMatchesByKey(invoke(localFile, remoteObjects))
                assert(result.isEmpty)
              }
            })
          }
        })
      }
      it("should return no matches by hash") {
        env.map({
          case (remoteObjects, _, _, _) => {
            env2.map({
              case (localFile) => {
                val result = getMatchesByHash(invoke(localFile, remoteObjects))
                assert(result.isEmpty)
              }
            })
          }
        })
      }
    }

    describe("when remote key exists and no others match hash") {
      val _ = env.map({
        case (remoteObjects, _, keyDiffHash, diffHash) => {
          it("should return match by key") {
            val result = getMatchesByKey(invoke(keyDiffHash, remoteObjects))
            assert(result.contains(diffHash))
          }
          it("should return only itself in match by hash") {
            val result = getMatchesByHash(invoke(keyDiffHash, remoteObjects))
            assert(result === Set((keyDiffHash.remoteKey, diffHash)))
          }
        }
      })
    }
  }

}
