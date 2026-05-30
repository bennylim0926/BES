package com.example.BES.respositories;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.BES.models.AppConfig;

public interface AppConfigRepository extends JpaRepository<AppConfig, Long> {
    Optional<AppConfig> findByKey(String key);
}
