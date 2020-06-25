package net.kemitix.thorp.storage.aws;

import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.transfer.TransferManagerConfiguration;
import com.amazonaws.services.s3.transfer.internal.TransferManagerUtils;
import net.kemitix.thorp.domain.HashGenerator;
import net.kemitix.thorp.domain.HashType;
import net.kemitix.thorp.domain.Hashes;
import net.kemitix.thorp.domain.MD5Hash;

import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

public class S3ETagGenerator implements HashGenerator {
    @Deprecated // Use hashFile
    public String eTag(Path path) throws IOException, NoSuchAlgorithmException {
        return hashFile(path);
    }
    @Override
    public String hashFile(Path path) throws IOException, NoSuchAlgorithmException {
        long partSize = calculatePartSize(path);
        long parts = numParts(path.toFile().length(), partSize);
        String eTagHex = eTagHex(path, partSize, parts);
        return String.format("%s-%d", eTagHex, parts);
    }

    @Override
    public Hashes hash(Path path) throws IOException, NoSuchAlgorithmException {
        HashType key = hashType();
        MD5Hash value = MD5Hash.create(hashFile(path));
        return Hashes.create(key, value);
    }

    @Override
    public MD5Hash hashChunk(Path path, Long index, long partSize) throws IOException, NoSuchAlgorithmException {
        return HashGenerator.generatorFor("MD5").hashChunk(path, index, partSize);
    }

    public List<Long> offsets(long totalFileSizeBytes, long optimalPartSize) {
        return LongStream
                .range(0, totalFileSizeBytes / optimalPartSize)
                .mapToObj(part -> part * optimalPartSize)
                .collect(Collectors.toList());
    }

    private long calculatePartSize(Path path) {
        return TransferManagerUtils.calculateOptimalPartSize(
                new PutObjectRequest("", "", path.toFile()),
                new TransferManagerConfiguration());
    }

    private long numParts(long length, long partSize) {
        long fullParts = Math.floorDiv(length, partSize);
        int incompleteParts = Math.floorMod(length, partSize) > 0
                ? 1
                : 0;
        return fullParts + incompleteParts;
    }

    private String eTagHex(Path path, long partSize, long parts) throws IOException, NoSuchAlgorithmException {
        HashGenerator hashGenerator = HashGenerator.generatorFor("MD5");
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        for (long i = 0; i < parts ; i++ ){
            md5.update(hashGenerator.hashChunk(path, i, partSize).digest());
        }
        return MD5Hash.digestAsString(md5.digest());
    }
    @Override
    public HashType hashType() {
        return net.kemitix.thorp.storage.aws.HashType.ETag;
    }
    @Override
    public String label() {
        return "ETag";
    }
}