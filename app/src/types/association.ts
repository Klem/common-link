export interface AssociationProfileDto {
  id: string;
  name: string;
  identifier: string;
  city: string | null;
  postalCode: string | null;
  contactName: string | null;
  description: string | null;
  verified: boolean;
}

export interface UpdateAssociationProfileRequest {
  contactName?: string;
  city?: string;
  postalCode?: string;
  description?: string;
}
