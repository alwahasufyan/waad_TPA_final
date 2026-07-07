package com.waad.tba.modules.visit.mapper;

import java.util.Map;
import org.springframework.stereotype.Component;

import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.visit.dto.VisitCreateDto;
import com.waad.tba.modules.visit.dto.VisitResponseDto;
import com.waad.tba.modules.visit.entity.Visit;
import com.waad.tba.modules.visit.entity.VisitType;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class VisitMapper {

    /**
     * PURE TRANSFORMATION (Refactored 2026-02-24)
     * Maps Visit entity to Response DTO using pre-resolved data.
     */
    public VisitResponseDto toResponseDto(Visit entity, String providerName, Map<String, Object> extraData) {
        if (entity == null)
            return null;

        String memberName = null;
        String memberNumber = null;
        Long employerId = null;
        String employerName = null;
        if (entity.getMember() != null) {
            memberName = entity.getMember().getFullName();
            memberNumber = entity.getMember().getCardNumber();
            if (entity.getMember().getEmployer() != null) {
                employerId = entity.getMember().getEmployer().getId();
                employerName = entity.getMember().getEmployer().getName();
            }
        }

        VisitResponseDto dto = VisitResponseDto.builder()
                .id(entity.getId())
                .memberId(entity.getMember() != null ? entity.getMember().getId() : null)
                .memberName(memberName)
                .memberNumber(memberNumber)
                .employerId(employerId)
                .employerName(employerName)
                .providerId(entity.getProviderId())
                .providerName(providerName)
                .visitDate(entity.getVisitDate())
                .doctorName(entity.getDoctorName())
                .specialty(entity.getSpecialty())
                .diagnosis(entity.getDiagnosis())
                .treatment(entity.getTreatment())
                .totalAmount(entity.getTotalAmount())
                .notes(entity.getNotes())
                .active(entity.getActive())
                .visitType(entity.getVisitType())
                .visitTypeLabel(entity.getVisitType() != null ? entity.getVisitType().getArabicLabel() : null)
                .medicalCategoryId(entity.getMedicalCategoryId())
                .medicalCategoryName(entity.getMedicalCategoryName())
                .medicalServiceId(entity.getMedicalServiceId())
                .medicalServiceCode(entity.getMedicalServiceCode())
                .medicalServiceName(entity.getMedicalServiceName())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();

        // Populate extra data if provided
        if (extraData != null) {
            dto.setClaimCount((Integer) extraData.getOrDefault("claimCount", 0));
            dto.setLatestClaimId((Long) extraData.get("latestClaimId"));
            dto.setLatestClaimStatus((String) extraData.get("latestClaimStatus"));
            dto.setLatestClaimStatusLabel((String) extraData.get("latestClaimStatusLabel"));

            dto.setPreAuthCount((Integer) extraData.getOrDefault("preAuthCount", 0));
            dto.setLatestPreAuthId((Long) extraData.get("latestPreAuthId"));
            dto.setLatestPreAuthStatus((String) extraData.get("latestPreAuthStatus"));
            dto.setLatestPreAuthStatusLabel((String) extraData.get("latestPreAuthStatusLabel"));
        }

        return dto;
    }

    public Visit toEntity(VisitCreateDto dto, Member member) {
        if (dto == null)
            return null;

        return Visit.builder()
                .member(member)
                .providerId(dto.getProviderId())
                .visitDate(dto.getVisitDate())
                .doctorName(dto.getDoctorName())
                .specialty(dto.getSpecialty())
                .diagnosis(dto.getDiagnosis())
                .treatment(dto.getTreatment())
                .totalAmount(dto.getTotalAmount())
                .notes(dto.getNotes())
                .visitType(dto.getVisitType() != null ? dto.getVisitType() : VisitType.OUTPATIENT)
                .active(true)
                .build();
    }

    public void updateEntityFromDto(Visit entity, VisitCreateDto dto, Member member) {
        if (dto == null)
            return;

        entity.setMember(member);
        entity.setProviderId(dto.getProviderId());
        entity.setVisitDate(dto.getVisitDate());
        entity.setDoctorName(dto.getDoctorName());
        entity.setSpecialty(dto.getSpecialty());
        entity.setDiagnosis(dto.getDiagnosis());
        entity.setTreatment(dto.getTreatment());
        entity.setTotalAmount(dto.getTotalAmount());
        entity.setNotes(dto.getNotes());

        if (dto.getVisitType() != null) {
            entity.setVisitType(dto.getVisitType());
        }
    }
}
