export const ComplianceAlertOrigin = {
  FREEZE_HIT_ONBOARDING: 'FREEZE_HIT_ONBOARDING',
  FREEZE_HIT_DONATION: 'FREEZE_HIT_DONATION',
} as const;
export type ComplianceAlertOrigin = typeof ComplianceAlertOrigin[keyof typeof ComplianceAlertOrigin];

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
  severity: string;
  status: string;
  createdAt: string;
  takenInChargeAt: string | null;
  ageSeconds: number;
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
  decision: string | null;
  decisionRationale: string | null;
  treasuryNotifiedAt: string | null;
  treasuryNotificationMethod: string | null;
  treasuryNotificationRef: string | null;
  freezeHistory: AuditLogEntryDto[];
}

export interface CloseAlertRequest {
  decision: string;
  rationale: string;
  treasuryNotifiedAt?: string;
  treasuryNotificationMethod?: string;
  treasuryNotificationRef?: string;
}
