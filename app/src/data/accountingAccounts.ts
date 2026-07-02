export interface AccountingAccount {
  code: string;
  label: string;
}

/**
 * French PCG (Plan Comptable Général) class 6 accounts — charges relevant to associations.
 * Static reference list for the expense-category autocomplete; not exhaustive.
 */
export const ACCOUNTING_ACCOUNTS: AccountingAccount[] = [
  { code: '601', label: 'Achats stockés — matières premières' },
  { code: '602', label: 'Achats stockés — autres approvisionnements' },
  { code: '604', label: "Achats d'études et prestations de services" },
  { code: '606', label: 'Achats non stockés de matières et fournitures' },
  { code: '607', label: 'Achats de marchandises' },
  { code: '611', label: 'Sous-traitance générale' },
  { code: '613', label: 'Locations' },
  { code: '614', label: 'Charges locatives et de copropriété' },
  { code: '615', label: 'Entretien et réparations' },
  { code: '616', label: "Primes d'assurance" },
  { code: '618', label: 'Divers (documentation, colloques...)' },
  { code: '621', label: "Personnel extérieur à l'association" },
  { code: '622', label: "Rémunérations d'intermédiaires et honoraires" },
  { code: '623', label: 'Publicité, publications, relations publiques' },
  { code: '624', label: 'Transports de biens et transports collectifs de personnel' },
  { code: '625', label: 'Déplacements, missions et réceptions' },
  { code: '626', label: 'Frais postaux et de télécommunications' },
  { code: '627', label: 'Services bancaires' },
  { code: '628', label: 'Divers (cotisations...)' },
  { code: '631', label: 'Impôts, taxes et versements assimilés sur rémunérations' },
  { code: '635', label: 'Autres impôts, taxes et versements assimilés' },
  { code: '641', label: 'Rémunérations du personnel' },
  { code: '645', label: 'Charges de sécurité sociale et de prévoyance' },
  { code: '646', label: 'Cotisations sociales personnelles' },
  { code: '647', label: 'Autres charges sociales' },
  { code: '648', label: 'Autres charges de personnel' },
  { code: '651', label: 'Redevances pour concessions, brevets, licences' },
  { code: '654', label: 'Pertes sur créances irrécouvrables' },
  { code: '658', label: 'Charges diverses de gestion courante' },
  { code: '661', label: "Charges d'intérêts" },
  { code: '671', label: 'Charges exceptionnelles sur opérations de gestion' },
  { code: '675', label: "Valeurs comptables des éléments d'actif cédés" },
  { code: '678', label: 'Autres charges exceptionnelles' },
  { code: '681', label: 'Dotations aux amortissements et provisions' },
];
