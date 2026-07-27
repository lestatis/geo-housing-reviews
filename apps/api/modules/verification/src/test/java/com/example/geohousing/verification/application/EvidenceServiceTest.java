package com.example.geohousing.verification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.geohousing.verification.domain.AccountRef;
import com.example.geohousing.verification.domain.EvidenceAccessAction;
import com.example.geohousing.verification.domain.EvidenceId;
import com.example.geohousing.verification.domain.ModeratorId;
import com.example.geohousing.verification.domain.PropertyRef;
import com.example.geohousing.verification.domain.RelationshipClaim;
import com.example.geohousing.verification.domain.VerificationCase;
import com.example.geohousing.verification.domain.VerificationCaseId;
import com.example.geohousing.verification.domain.VerificationCaseNotFoundException;
import com.example.geohousing.verification.domain.VerificationEvidence;
import com.example.geohousing.verification.domain.VerificationMethod;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EvidenceServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-23T10:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final AccountRef OWNER = AccountRef.of(UUID.randomUUID());
  private static final AccountRef STRANGER = AccountRef.of(UUID.randomUUID());
  private static final AccountRef MODERATOR_ACCOUNT = AccountRef.of(UUID.randomUUID());
  // Unmistakably synthetic: never anything that could be taken for a real document.
  private static final byte[] SYNTHETIC =
      "SYNTHETIC-TEST-EVIDENCE-NOT-A-REAL-DOCUMENT".getBytes(StandardCharsets.UTF_8);

  private final InMemoryVerificationCaseRepository cases = new InMemoryVerificationCaseRepository();
  private final InMemoryEvidenceRepository evidenceRepository = new InMemoryEvidenceRepository();
  private final RecordingEvidenceAccessAudit accessAudit = new RecordingEvidenceAccessAudit();
  private final InMemoryEvidenceStore store = new InMemoryEvidenceStore();
  private final EvidenceService service =
      new EvidenceService(
          cases,
          evidenceRepository,
          accessAudit,
          store,
          new EvidenceRetentionPolicy(Duration.ofDays(30), Duration.ofDays(7)),
          1024,
          CLOCK);

  private VerificationCase storeCase(VerificationMethod method) {
    VerificationCase verificationCase =
        VerificationCase.open(
            VerificationCaseId.of(UUID.randomUUID()),
            OWNER,
            PropertyRef.of(UUID.randomUUID()),
            RelationshipClaim.OWNER,
            method,
            1,
            CLOCK);
    cases.create(verificationCase);
    return verificationCase;
  }

  private static InputStream synthetic() {
    return new ByteArrayInputStream(SYNTHETIC);
  }

  private VerificationEvidence attachTo(VerificationCase verificationCase) {
    return service.attach(
        verificationCase.id(), VerificationViewer.account(OWNER), "application/pdf", synthetic());
  }

  @Test
  void theOwnerAttachesEvidenceAndTheMetadataRecordsWhatWasStored() {
    VerificationCase documentCase = storeCase(VerificationMethod.DOCUMENT);

    VerificationEvidence evidence = attachTo(documentCase);

    assertThat(evidence.caseId()).isEqualTo(documentCase.id());
    assertThat(evidence.contentType()).isEqualTo("application/pdf");
    assertThat(evidence.sizeBytes()).isEqualTo(SYNTHETIC.length);
    assertThat(evidence.sha256()).hasSize(64);
    // The deadline is set at upload, from configuration.
    assertThat(evidence.retentionDeadline()).isEqualTo(NOW.plus(Duration.ofDays(30)));
    assertThat(evidence.isDeleted()).isFalse();
    // The object really landed, under an unguessable key.
    assertThat(store.objects).containsKey(evidence.storageReference());
    assertThat(evidence.storageReference())
        .startsWith("evidence/" + documentCase.id().value() + "/");
  }

  @Test
  void aSignalCaseTakesNoEvidence() {
    VerificationCase signalCase = storeCase(VerificationMethod.INVITATION);

    assertThatThrownBy(() -> attachTo(signalCase)).isInstanceOf(EvidenceNotAcceptedException.class);
    assertThat(store.objects).isEmpty();
  }

  @Test
  void aDecidedCaseTakesNoMoreEvidence() {
    VerificationCase documentCase = storeCase(VerificationMethod.DOCUMENT);
    VerificationCase loaded = cases.findById(documentCase.id()).orElseThrow();
    loaded.approve(ModeratorId.of(MODERATOR_ACCOUNT.value()), "DOC_OK", null, CLOCK);
    cases.save(loaded);

    assertThatThrownBy(() -> attachTo(documentCase))
        .isInstanceOf(EvidenceNotAcceptedException.class);
  }

  @Test
  void aStrangerIsNotEvenToldTheCaseExists() {
    VerificationCase documentCase = storeCase(VerificationMethod.DOCUMENT);

    assertThatThrownBy(
            () ->
                service.attach(
                    documentCase.id(),
                    VerificationViewer.account(STRANGER),
                    "application/pdf",
                    synthetic()))
        .isInstanceOf(VerificationCaseNotFoundException.class);
  }

  @Test
  void aModeratorCannotUploadEvidenceIntoACaseTheyWillJudge() {
    VerificationCase documentCase = storeCase(VerificationMethod.DOCUMENT);

    // Visible to a moderator, but uploading is the owner's act — otherwise the reviewer could
    // manufacture what they then judge.
    assertThatThrownBy(
            () ->
                service.attach(
                    documentCase.id(),
                    VerificationViewer.moderator(MODERATOR_ACCOUNT),
                    "application/pdf",
                    synthetic()))
        .isInstanceOf(VerificationAccessDeniedException.class);
    assertThat(store.objects).isEmpty();
  }

  @Test
  void anUnsupportedContentTypeIsRefusedBeforeAnythingIsStored() {
    VerificationCase documentCase = storeCase(VerificationMethod.DOCUMENT);

    assertThatThrownBy(
            () ->
                service.attach(
                    documentCase.id(),
                    VerificationViewer.account(OWNER),
                    "application/zip",
                    synthetic()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(store.objects).isEmpty();
    assertThat(evidenceRepository.byId).isEmpty();
  }

  @Test
  void anOversizedUploadLeavesNoMetadataRow() {
    VerificationCase documentCase = storeCase(VerificationMethod.DOCUMENT);

    assertThatThrownBy(
            () ->
                service.attach(
                    documentCase.id(),
                    VerificationViewer.account(OWNER),
                    "image/png",
                    new ByteArrayInputStream(new byte[2048])))
        .isInstanceOf(EvidenceTooLargeException.class);
    assertThat(evidenceRepository.byId).isEmpty();
  }

  @Test
  void aModeratorReadReturnsTheBytesAndIsAuditedFirst() throws IOException {
    VerificationCase documentCase = storeCase(VerificationMethod.DOCUMENT);
    VerificationEvidence evidence = attachTo(documentCase);

    try (InputStream read =
        service.read(evidence.id(), VerificationViewer.moderator(MODERATOR_ACCOUNT))) {
      assertThat(read.readAllBytes()).isEqualTo(SYNTHETIC);
    }

    assertThat(accessAudit.events).hasSize(1);
    assertThat(accessAudit.last().action()).isEqualTo(EvidenceAccessAction.READ);
    assertThat(accessAudit.last().evidenceId()).isEqualTo(evidence.id());
    assertThat(accessAudit.last().accessorAccountId()).contains(MODERATOR_ACCOUNT.value());
  }

  @Test
  void anOwnerCannotReadTheirOwnEvidenceBackAndTheAttemptRevealsNothing() {
    VerificationCase documentCase = storeCase(VerificationMethod.DOCUMENT);
    VerificationEvidence evidence = attachTo(documentCase);

    // Reading is a disclosure restricted to moderators; a non-moderator is told it does not exist
    // rather than that they are forbidden.
    assertThatThrownBy(() -> service.read(evidence.id(), VerificationViewer.account(OWNER)))
        .isInstanceOf(EvidenceNotFoundException.class);
    // A refused read leaves no audit row, because nothing was disclosed.
    assertThat(accessAudit.events).isEmpty();
  }

  @Test
  void readingUnknownEvidenceIsNotFound() {
    assertThatThrownBy(
            () ->
                service.read(
                    EvidenceId.of(UUID.randomUUID()),
                    VerificationViewer.moderator(MODERATOR_ACCOUNT)))
        .isInstanceOf(EvidenceNotFoundException.class);
  }

  @Test
  void deletingACasesEvidenceRemovesTheObjectKeepsTheRowAndIsAudited() {
    VerificationCase documentCase = storeCase(VerificationMethod.DOCUMENT);
    VerificationEvidence evidence = attachTo(documentCase);

    int deleted = service.deleteForCase(documentCase.id(), MODERATOR_ACCOUNT.value());

    assertThat(deleted).isEqualTo(1);
    // The object is gone from storage …
    assertThat(store.objects).isEmpty();
    // … but the metadata row survives, stamped, as proof of what was held and released.
    VerificationEvidence stored = evidenceRepository.findById(evidence.id()).orElseThrow();
    assertThat(stored.isDeleted()).isTrue();
    assertThat(stored.deletedAt()).contains(NOW);
    assertThat(accessAudit.last().action()).isEqualTo(EvidenceAccessAction.DELETE);
    assertThat(accessAudit.last().accessorAccountId()).contains(MODERATOR_ACCOUNT.value());
  }

  @Test
  void deletingIsIdempotentAndReadingDeletedEvidenceSaysSoPlainly() {
    VerificationCase documentCase = storeCase(VerificationMethod.DOCUMENT);
    VerificationEvidence evidence = attachTo(documentCase);
    service.deleteForCase(documentCase.id(), MODERATOR_ACCOUNT.value());

    // A second call deletes nothing and writes no second audit row.
    int auditRows = accessAudit.events.size();
    assertThat(service.deleteForCase(documentCase.id(), MODERATOR_ACCOUNT.value())).isZero();
    assertThat(accessAudit.events).hasSize(auditRows);

    // A moderator asking for deleted evidence is told it is gone, not that it never existed.
    assertThatThrownBy(
            () -> service.read(evidence.id(), VerificationViewer.moderator(MODERATOR_ACCOUNT)))
        .isInstanceOf(EvidenceContentGoneException.class);
  }

  @Test
  void listingACasesEvidenceReturnsMetadataOnly() {
    VerificationCase documentCase = storeCase(VerificationMethod.DOCUMENT);
    attachTo(documentCase);

    assertThat(
            service.listForCase(documentCase.id(), VerificationViewer.moderator(MODERATOR_ACCOUNT)))
        .hasSize(1);
    assertThatThrownBy(
            () -> service.listForCase(documentCase.id(), VerificationViewer.account(STRANGER)))
        .isInstanceOf(VerificationCaseNotFoundException.class);
  }
}
