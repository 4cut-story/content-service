package net.java21.fourcut.content.storage;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** MinIO에 S3 API로 붙는다 (path-style, SigV4). */
public class S3ObjectStorage implements ObjectStorage {

  private final S3Client client;
  private final S3Presigner presigner;
  private final String bucket;

  public S3ObjectStorage(S3Client client, S3Presigner presigner, String bucket) {
    this.client = client;
    this.presigner = presigner;
    this.bucket = bucket;
  }

  @Override
  public String presignGet(String key, Duration ttl) {
    return presigner
        .presignGetObject(
            r -> r.signatureDuration(ttl).getObjectRequest(g -> g.bucket(bucket).key(key)))
        .url()
        .toString();
  }

  @Override
  public void delete(Collection<String> keys) {
    List<ObjectIdentifier> ids =
        keys.stream()
            .filter(k -> k != null && !k.isBlank())
            .map(k -> ObjectIdentifier.builder().key(k).build())
            .toList();
    if (ids.isEmpty()) {
      return;
    }
    client.deleteObjects(
        DeleteObjectsRequest.builder()
            .bucket(bucket)
            .delete(Delete.builder().objects(ids).quiet(true).build())
            .build());
  }
}
