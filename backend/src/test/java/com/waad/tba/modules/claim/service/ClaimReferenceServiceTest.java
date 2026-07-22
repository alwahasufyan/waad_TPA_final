package com.waad.tba.modules.claim.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.waad.tba.modules.claim.entity.ProviderClaimSequence;
import com.waad.tba.modules.claim.repository.ProviderClaimSequenceRepository;

/**
 * CLAIM-NUMBERING-1: verifies official claim reference generation —
 * format, per-provider sequence progression, and the
 * ensure-row-exists-then-lock ordering that keeps it concurrency-safe.
 */
@ExtendWith(MockitoExtension.class)
class ClaimReferenceServiceTest {

    @Mock private ProviderClaimSequenceRepository sequenceRepository;

    @InjectMocks
    private ClaimReferenceService claimReferenceService;

    @Test
    void generateNextReference_firstClaimForProvider_returnsSequenceOne() {
        when(sequenceRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(ProviderClaimSequence.builder().providerId(1L).nextValue(1L).build()));

        String reference = claimReferenceService.generateNextReference(1L);

        assertThat(reference).isEqualTo("CLM-P001-000001");
    }

    @Test
    void generateNextReference_secondClaimForSameProvider_incrementsSequence() {
        when(sequenceRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(ProviderClaimSequence.builder().providerId(1L).nextValue(7L).build()));

        String reference = claimReferenceService.generateNextReference(1L);

        assertThat(reference).isEqualTo("CLM-P001-000007");
    }

    @Test
    void generateNextReference_savesIncrementedSequenceAfterIssuing() {
        when(sequenceRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(ProviderClaimSequence.builder().providerId(1L).nextValue(1L).build()));

        claimReferenceService.generateNextReference(1L);

        ArgumentCaptor<ProviderClaimSequence> captor = ArgumentCaptor.forClass(ProviderClaimSequence.class);
        verify(sequenceRepository).save(captor.capture());
        assertThat(captor.getValue().getNextValue()).isEqualTo(2L);
    }

    @Test
    void generateNextReference_differentProviders_eachStartAtOneIndependently() {
        when(sequenceRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(ProviderClaimSequence.builder().providerId(1L).nextValue(1L).build()));
        when(sequenceRepository.findByIdForUpdate(2L))
                .thenReturn(Optional.of(ProviderClaimSequence.builder().providerId(2L).nextValue(1L).build()));

        String referenceProvider1 = claimReferenceService.generateNextReference(1L);
        String referenceProvider2 = claimReferenceService.generateNextReference(2L);

        assertThat(referenceProvider1).isEqualTo("CLM-P001-000001");
        assertThat(referenceProvider2).isEqualTo("CLM-P002-000001");
    }

    @Test
    void generateNextReference_padsLargeProviderAndSequenceValues() {
        when(sequenceRepository.findByIdForUpdate(123L))
                .thenReturn(Optional.of(ProviderClaimSequence.builder().providerId(123L).nextValue(456789L).build()));

        String reference = claimReferenceService.generateNextReference(123L);

        assertThat(reference).isEqualTo("CLM-P123-456789");
    }

    @Test
    void generateNextReference_ensuresRowExistsBeforeLockingIt() {
        when(sequenceRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(ProviderClaimSequence.builder().providerId(1L).nextValue(1L).build()));

        claimReferenceService.generateNextReference(1L);

        verify(sequenceRepository, times(1)).ensureRowExists(1L);
        verify(sequenceRepository, times(1)).findByIdForUpdate(1L);
    }

    @Test
    void generateNextReference_nullProviderId_throwsAndNeverTouchesRepository() {
        assertThatThrownBy(() -> claimReferenceService.generateNextReference(null))
                .isInstanceOf(IllegalArgumentException.class);

        verify(sequenceRepository, never()).ensureRowExists(any());
        verify(sequenceRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void generateNextReference_rowMissingAfterEnsure_throwsIllegalState() {
        when(sequenceRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> claimReferenceService.generateNextReference(1L))
                .isInstanceOf(IllegalStateException.class);
    }
}
