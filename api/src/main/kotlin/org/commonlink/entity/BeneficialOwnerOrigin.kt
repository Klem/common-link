package org.commonlink.entity

/** Origine de l'information identifiant un bénéficiaire effectif. */
enum class BeneficialOwnerOrigin {
    /** Information déclarée par l'association elle-même. */
    DECLARED,

    /** Nom issu du scan automatisé des registres publics, confirmé par le curateur. */
    REGISTRY,

    /** Nom relevé manuellement sur les statuts ou procès-verbaux de nomination déposés. */
    STATUTS,
}
