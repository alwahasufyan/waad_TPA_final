package com.waad.tba.modules.medicalclassification.repository;

import com.waad.tba.modules.medicalclassification.entity.ClassificationSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClassificationSettingRepository extends JpaRepository<ClassificationSetting, Long> {

    Optional<ClassificationSetting> findBySettingKey(String settingKey);
}
