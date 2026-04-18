package com.hackathon.features.bans;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserBanRepository extends JpaRepository<UserBan, UUID> {
  boolean existsByBannerIdAndBannedId(UUID bannerId, UUID bannedId);

  List<UserBan> findByBannerId(UUID bannerId);

  void deleteByBannerIdAndBannedId(UUID bannerId, UUID bannedId);
}
