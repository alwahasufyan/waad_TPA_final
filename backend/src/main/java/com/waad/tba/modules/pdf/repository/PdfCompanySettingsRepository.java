package com.waad.tba.modules.pdf.repository;

import com.waad.tba.modules.pdf.entity.PdfCompanySettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * PDF Company Settings Repository
 * 
 * Data access layer for PDF company settings.
 * 
 * @since 2026-01-11
 */
@Repository
public interface PdfCompanySettingsRepository extends JpaRepository<PdfCompanySettings, Long> {
    
    /**
     * Find active settings
     */
    @Query("SELECT s FROM PdfCompanySettings s WHERE s.isActive = true ORDER BY s.id DESC LIMIT 1")
    Optional<PdfCompanySettings> findActiveSettings();
    
    /**
     * Find latest settings (active or not)
     */
    @Query("SELECT s FROM PdfCompanySettings s ORDER BY s.id DESC LIMIT 1")
    Optional<PdfCompanySettings> findLatestSettings();
}
