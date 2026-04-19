package com.hackathon.shared.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "storage.s3")
public class StorageProperties {
  private String endpoint;
  private String region;
  private String accessKey;
  private String secretKey;
  private String bucket;
  private boolean pathStyleAccess = true;
}
