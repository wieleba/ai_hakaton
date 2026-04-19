package com.hackathon.shared.storage;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class S3StorageService implements StorageService {
  private final S3Client s3;
  private final StorageProperties props;

  @PostConstruct
  void ensureBucket() {
    try {
      s3.headBucket(HeadBucketRequest.builder().bucket(props.getBucket()).build());
      log.info("Storage bucket {} exists", props.getBucket());
    } catch (NoSuchBucketException e) {
      log.info("Creating storage bucket {}", props.getBucket());
      s3.createBucket(CreateBucketRequest.builder().bucket(props.getBucket()).build());
    } catch (software.amazon.awssdk.services.s3.model.S3Exception e) {
      if (e.statusCode() == 404) {
        log.info("Creating storage bucket {}", props.getBucket());
        s3.createBucket(CreateBucketRequest.builder().bucket(props.getBucket()).build());
      } else {
        throw e;
      }
    }
  }

  @Override
  public String store(InputStream content, long size, String mimeType) {
    String key = UUID.randomUUID().toString();
    s3.putObject(
        PutObjectRequest.builder()
            .bucket(props.getBucket())
            .key(key)
            .contentType(mimeType)
            .contentLength(size)
            .build(),
        RequestBody.fromInputStream(content, size));
    return key;
  }

  @Override
  public InputStream load(String storageKey) {
    ResponseInputStream<GetObjectResponse> resp =
        s3.getObject(GetObjectRequest.builder().bucket(props.getBucket()).key(storageKey).build());
    return resp;
  }

  @Override
  public void delete(String storageKey) {
    s3.deleteObject(DeleteObjectRequest.builder().bucket(props.getBucket()).key(storageKey).build());
  }
}
