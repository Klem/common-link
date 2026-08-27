import type { LegalAcceptanceDto } from '@/types/legal';

export const ComplianceAlertOrigin = {
  FREEZE_HIT_ONBOARDING: 'FREEZE_HIT_ONBOARDING',
  FREEZE_HIT_DONATION: 'FREEZE_HIT_DONATION',
  /** A mandatory freeze screening could not be performed. Not a favorable outcome. */
  SCREENING_UNAVAILABLE: 'SCREENING_UNAVAILABLE',
  /** A public visitor reported a campaign's content (IC-44). */
  CAMPAIGN_REPORT: 'CAMPAIGN_REPORT',
} as const;
export type ComplianceAlertOrigin = typeof ComplianceAlertOrigin[keyof typeof ComplianceAlertOrigin];

export const ComplianceAlertSubjectType = {
  ASSOCIATION: 'ASSOCIATION',
  BENEFICIAL_OWNER: 'BENEFICIAL_OWNER',
  DONOR: 'DONOR',
  SYSTEM: 'SYSTEM',
} as const;
export type ComplianceAlertSubjectType = typeof ComplianceAlertSubjectType[keyof typeof ComplianceAlertSubjectType];

export const SanctionedNature = {
  PHYSICAL_PERSON: 'PHYSICAL_PERSON',
  LEGAL_ENTITY: 'LEGAL_ENTITY',
  VESSEL: 'VESSEL',
} as const;
export type SanctionedNature = typeof SanctionedNature[keyof typeof SanctionedNature];

export const ComplianceAlertSeverity = {
  HIGH: 'HIGH',
  MEDIUM: 'MEDIUM',
  LOW: 'LOW',
} as const;
export type ComplianceAlertSeverity = typeof ComplianceAlertSeverity[keyof typeof ComplianceAlertSeverity];

export const ComplianceAlertStatus = {
  PENDING: 'PENDING',
  IN_REVIEW: 'IN_REVIEW',
  CLOSED: 'CLOSED',
} as const;
export type ComplianceAlertStatus = typeof ComplianceAlertStatus[keyof typeof ComplianceAlertStatus];

export const ComplianceAlertDecision = {
  LEGITIMATE: 'LEGITIMATE',
  SUSPICIOUS: 'SUSPICIOUS',
  FALSE_POSITIVE: 'FALSE_POSITIVE',
} as const;
export type ComplianceAlertDecision = typeof ComplianceAlertDecision[keyof typeof ComplianceAlertDecision];

export interface ComplianceAlertSummaryDto {
  id: string;
  origin: string;
  subjectType: string;
  subjectId: string | null;
  /** Association / beneficial owner / donor designation. Null when the dossier no longer resolves. */
  subjectLabel: string | null;
  severity: string;
  status: string;
  createdAt: string;
  takenInChargeAt: string | null;
  ageSeconds: number;
}

/**
 * One correspondence between a screened name and an entry of the asset-freeze register.
 *
 * `screenedNormalizedName` is the value that actually produced `score`: "TECHNO +" is compared as
 * "TECHNO", which is what makes a 0.93 against "TECHNOLAB" intelligible. `matchedLegalReference`
 * names the sanctions programme and is the field a false-positive ruling usually rests on.
 */
export interface FreezeScreeningMatchDto {
  subjectType: string;
  subjectId: string;
  screenedNormalizedName: string;
  sanctionedIdRegistre: number;
  matchedName: string;
  matchedNature: string;
  matchedLegalReference: string | null;
  matchedDateOfBirth: string | null;
  score: number;
  scoreThreshold: number;
  algorithm: string;
  registryPublicationDate: string;
}

/** A previous ruling on the same subject. Informative only — it suppresses no alert. */
export interface PriorDecisionDto {
  alertId: string;
  origin: string;
  decision: string | null;
  decisionRationale: string | null;
  createdAt: string;
}

export interface AuditLogEntryDto {
  sequenceNo: number;
  eventType: string;
  subjectId: string | null;
  actorUserId: string | null;
  payload: Record<string, unknown>;
  occurredAt: string;
}

export interface ComplianceAlertDetailDto extends ComplianceAlertSummaryDto {
  takenInChargeBy: string | null;
  takenInChargeByLabel: string | null;
  decision: string | null;
  decisionRationale: string | null;
  treasuryNotifiedAt: string | null;
  treasuryNotificationMethod: string | null;
  treasuryNotificationRef: string | null;
  freezeHistory: AuditLogEntryDto[];
  /** Register entries actually matched. Empty for a SCREENING_UNAVAILABLE alert. */
  matches: FreezeScreeningMatchDto[];
  priorDecisions: PriorDecisionDto[];
  /** Public-registry identity of the subject association. Null for donor / beneficial-owner subjects. */
  subjectRegistry: SubjectRegistryDto | null;
  /** Every report received, oldest first. Populated only for a CAMPAIGN_REPORT alert. */
  campaignReports: CampaignReportEntryDto[];
}

/** One report entry, read back from the compliance journal (IC-44). */
export interface CampaignReportEntryDto {
  campaignId: string | null;
  message: string;
  reporterEmail: string | null;
  occurredAt: string;
}

/**
 * Public-registry identity of the subject association — the discriminating side of the comparison.
 * An active RNA plus a verified SIREN is what separates a French loi-1901 from the foreign entity
 * a sanctions programme designates, however closely the two names score.
 */
export interface SubjectRegistryDto {
  siren: string | null;
  rna: string | null;
  scopeVerdict: string;
  associationExists: boolean | null;
  rnaActive: boolean | null;
  checkedAt: string;
}

export interface CloseAlertRequest {
  decision: string;
  rationale: string;
  treasuryNotifiedAt?: string;
  treasuryNotificationMethod?: string;
  treasuryNotificationRef?: string;
}

export const ScopeVerdict = {
  IN_SCOPE: 'IN_SCOPE',
  OUT_OF_SCOPE: 'OUT_OF_SCOPE',
  UNDETERMINED: 'UNDETERMINED',
} as const;
export type ScopeVerdict = typeof ScopeVerdict[keyof typeof ScopeVerdict];

export interface ComplianceRegistryScanSummaryDto {
  associationId: string;
  associationName: string;
  associationExists: boolean | null;
  rnaActive: boolean | null;
  scopeVerdict: ScopeVerdict;
  warningCount: number;
  checkedAt: string;
  siren: string | null;
  rna: string | null;
}

/** One row of the compliance « Associations » index. */
export interface ComplianceAssociationSummaryDto {
  id: string;
  name: string;
  identifier: string;
  status: string;
  verificationStatus: string;
  riskLevel: string;
}

/**
 * Full compliance dossier of one association — status, KYB standing, and every legal-identity
 * field held on the association profile.
 */
export interface ComplianceAssociationDetailDto {
  id: string;
  name: string;
  identifier: string;
  siren: string | null;
  addressLine1: string | null;
  legalObject: string | null;
  signerName: string | null;
  signerRole: string | null;
  city: string | null;
  postalCode: string | null;
  creationYear: number | null;
  contactName: string | null;
  contactEmail: string | null;
  phone: string | null;
  status: string;
  verificationStatus: string;
  verifiedAt: string | null;
  riskLevel: string;
  riskLevelAssessedAt: string | null;
  riskClassificationVersion: string | null;
}

/**
 * Why a campaign's DRAFT→LIVE publish attempt was refused — one value per guard in
 * `CampaignService.preparePublish()`. Labels shown to the compliance officer must match the
 * table in `docs/legal/E6-tracage-refus-metriques-rapport-annuel.md` §4.1, not a paraphrase.
 */
export const CampaignReviewRefusalReason = {
  GOAL_MISSING: 'GOAL_MISSING',
  SCHEDULE_MISSING: 'SCHEDULE_MISSING',
  BUDGET_UNBALANCED: 'BUDGET_UNBALANCED',
  IMPACT_GOALS_MISSING: 'IMPACT_GOALS_MISSING',
  KYB_NOT_VERIFIED: 'KYB_NOT_VERIFIED',
  BANK_NOT_CONNECTED: 'BANK_NOT_CONNECTED',
  BANK_CONNECTION_BROKEN: 'BANK_CONNECTION_BROKEN',
  BANK_KYC_INCOMPLETE: 'BANK_KYC_INCOMPLETE',
  CGU_NOT_ACCEPTED: 'CGU_NOT_ACCEPTED',
} as const;
export type CampaignReviewRefusalReason =
  typeof CampaignReviewRefusalReason[keyof typeof CampaignReviewRefusalReason];

/** Event types written on `campaign_review_event` (see [AuditLogEntryDto.eventType]). */
export const CampaignReviewEventType = {
  CAMPAIGN_REVIEW_RETAINED: 'CAMPAIGN_REVIEW_RETAINED',
  CAMPAIGN_REVIEW_REFUSED: 'CAMPAIGN_REVIEW_REFUSED',
} as const;
export type CampaignReviewEventType =
  typeof CampaignReviewEventType[keyof typeof CampaignReviewEventType];

/**
 * One donor's CGU/CGV acceptance rows for a single campaign, grouped — a donor who gave twice to
 * the same campaign has one group with every document from both donations, not two rows.
 */
export interface DonorLegalAcceptanceGroupDto {
  donorId: string;
  donorName: string | null;
  donorEmail: string | null;
  acceptances: LegalAcceptanceDto[];
}
