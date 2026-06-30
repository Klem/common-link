import { getTranslations } from 'next-intl/server';
import { LegalContent } from '@/components/layout/LegalContent';

export async function generateMetadata({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'metadata.cgu' });
  return {
    title: t('title'),
    description: t('description'),
  };
}

export default function CGUPage() {
  return (
    <LegalContent title="Conditions générales d'utilisation" lastUpdated="29 juin 2026">
      <p>
        Les présentes conditions générales d'utilisation ont pour objet de définir les conditions d'accès et
        d'utilisation du site <a href="https://www.common-link.org/">https://www.common-link.org/</a> et des
        services proposés par CommonLink.
      </p>

      <h2>1. Définitions</h2>
      <p>Dans les présentes conditions générales, les termes ci-dessous ont la signification suivante :</p>
      <p><strong>CommonLink</strong> : la société CommonLink, SAS, immatriculée au RCS d'Antibes sous le numéro 105 153 928, dont le siège social est situé 1047 Chemin des Impiniers, 06220 Vallauris, France.</p>
      <p><strong>Site</strong> : le site internet accessible à l'adresse <a href="https://www.common-link.org/">https://www.common-link.org/</a>.</p>
      <p><strong>Plateforme</strong> : l'environnement numérique proposé par CommonLink permettant notamment à des organismes bénéficiaires de présenter des campagnes de collecte et à des donateurs de réaliser, suivre et documenter leurs dons.</p>
      <p><strong>Utilisateur</strong> : toute personne accédant au Site ou utilisant les services CommonLink.</p>
      <p><strong>Donateur</strong> : toute personne réalisant un don au bénéfice d'un organisme bénéficiaire via la Plateforme.</p>
      <p><strong>Organisme bénéficiaire</strong> : toute association, fondation, fonds de dotation, ONG ou structure éligible utilisant CommonLink pour présenter une campagne, recevoir des dons ou documenter l'utilisation des fonds collectés.</p>
      <p><strong>Campagne</strong> : page ou espace dédié à une opération de collecte présentée par un organisme bénéficiaire sur la Plateforme.</p>

      <h2>2. Objet du service</h2>
      <p>CommonLink est une plateforme de dons permettant aux associations, fondations, fonds de dotation, ONG et entreprises mécènes de collecter, suivre et documenter l'utilisation des fonds collectés, avec des mécanismes de transparence et de preuve d'utilisation.</p>
      <p>La Plateforme permet notamment :</p>
      <ul>
        <li>la présentation d'organismes bénéficiaires et de campagnes ;</li>
        <li>la création d'un compte donateur ;</li>
        <li>la réalisation de dons ponctuels ;</li>
        <li>l'accès à un historique de dons ;</li>
        <li>la génération et la transmission de reçus fiscaux pour le compte des organismes bénéficiaires, lorsque les conditions applicables sont réunies ;</li>
        <li>l'accès à des informations de suivi relatives aux campagnes ;</li>
        <li>la mise à disposition d'éléments de transparence et de preuve d'utilisation des fonds.</li>
      </ul>

      <h2>3. Statut de CommonLink</h2>
      <p>CommonLink est une société commerciale de l'économie sociale et solidaire. Une demande d'agrément ESUS a été déposée.</p>
      <p>CommonLink est en cours de démarche d'immatriculation auprès de l'ORIAS en qualité d'intermédiaire en financement participatif.</p>
      <p>CommonLink n'est pas un établissement de paiement. Les services de paiement sont fournis par des prestataires habilités.</p>
      <p>Les dons sont réalisés directement au bénéfice des organismes bénéficiaires. CommonLink n'encaisse pas juridiquement les fonds pour son propre compte.</p>

      <h2>4. Acceptation des conditions générales</h2>
      <p>L'accès au Site implique l'acceptation des présentes conditions générales d'utilisation.</p>
      <p>Pour réaliser un don ou créer un compte, l'utilisateur doit accepter expressément les présentes conditions générales.</p>
      <p>CommonLink peut modifier les présentes conditions générales afin de tenir compte notamment de l'évolution du service, de la réglementation, des partenaires techniques ou des conditions d'utilisation des prestataires. La version applicable est celle en vigueur à la date d'utilisation du service.</p>

      <h2>5. Accès au Site et à la Plateforme</h2>
      <p>Le Site est accessible librement à tout utilisateur disposant d'un accès internet. Certaines fonctionnalités nécessitent la création d'un compte.</p>
      <p>CommonLink peut suspendre, limiter ou interrompre l'accès au Site ou à la Plateforme pour des raisons de maintenance, sécurité, mise à jour, force majeure ou tout autre motif légitime.</p>

      <h2>6. Création de compte donateur</h2>
      <p>La création d'un compte donateur est nécessaire pour réaliser un don via la Plateforme.</p>
      <p>Le donateur s'engage à fournir des informations exactes, complètes et à jour, et est responsable de la confidentialité de ses identifiants.</p>
      <p>En cas d'utilisation non autorisée de son compte, le donateur doit en informer CommonLink sans délai à l'adresse : <a href="mailto:contact@common-link.org">contact@common-link.org</a></p>

      <h2>7. Conditions applicables aux donateurs</h2>
      <p>La Plateforme est destinée aux personnes majeures. Les mineurs ne peuvent utiliser la Plateforme qu'avec l'autorisation de leur représentant légal.</p>
      <p>Le donateur choisit une campagne présentée sur la Plateforme et réalise un don au bénéfice de l'organisme bénéficiaire concerné. La sélection d'une campagne entraîne une affectation globale du don à cette campagne.</p>

      <h2>8. Dons</h2>
      <p>Les dons réalisés via CommonLink sont ponctuels au lancement. Des fonctionnalités de dons récurrents pourront être proposées ultérieurement.</p>
      <p>Les dons sont réalisés en euros. Le don est juridiquement acquis à l'organisme bénéficiaire dès sa réception, indépendamment de l'atteinte éventuelle de l'objectif affiché pour une campagne.</p>

      <h2>9. Paiement</h2>
      <p>Les moyens de paiement proposés au lancement sont la carte bancaire et le prélèvement SEPA. Les paiements sont traités par un ou plusieurs prestataires de services de paiement habilités.</p>
      <p>CommonLink ne collecte pas, ne stocke pas et ne traite pas directement les données complètes de carte bancaire.</p>

      <h2>10. Contribution volontaire à CommonLink</h2>
      <p>Lors de la réalisation d'un don, le donateur peut laisser une contribution volontaire à CommonLink. Cette contribution est facultative et peut être ramenée à zéro avant validation du paiement.</p>

      <h2>11. Frais applicables aux organismes bénéficiaires</h2>
      <p>CommonLink facture aux organismes bénéficiaires des frais de service de <strong>8 % HT</strong> et des frais de paiement et techniques de <strong>2 %</strong> du montant des dons collectés, facturés mensuellement.</p>
      <p>Les tarifs applicables sont précisés sur la <a href="/tarifs">page Tarifs</a> du Site.</p>

      <h2>12. Remboursements</h2>
      <p>Les dons sont en principe définitifs et non remboursables. Un remboursement peut toutefois être étudié dans les cas suivants :</p>
      <ul>
        <li>double paiement ;</li>
        <li>erreur manifeste de montant ;</li>
        <li>fraude ;</li>
        <li>paiement non autorisé ;</li>
        <li>campagne annulée ;</li>
        <li>organisme bénéficiaire retiré de la Plateforme ;</li>
        <li>situation exceptionnelle appréciée par CommonLink.</li>
      </ul>
      <p>Les demandes doivent être adressées à : <a href="mailto:contact@common-link.org">contact@common-link.org</a></p>

      <h2>13. Reçus fiscaux</h2>
      <p>Lorsque l'organisme bénéficiaire est éligible, CommonLink génère et transmet des reçus fiscaux pour le compte de l'organisme bénéficiaire.</p>
      <p>Les certificats numériques, NFT ou éléments de preuve complémentaires ne constituent pas des reçus fiscaux.</p>

      <h2>14. Transparence et preuve d'utilisation</h2>
      <p>CommonLink met à disposition des outils de transparence permettant de suivre certains flux financiers et de présenter des preuves d'utilisation fondées sur les opérations enregistrées.</p>
      <p>CommonLink ne garantit pas que chaque euro collecté soit traçable de manière exhaustive dans toutes les situations, et ne garantit pas l'usage final des fonds par l'organisme bénéficiaire.</p>

      <h2>15. Conditions applicables aux organismes bénéficiaires</h2>
      <p>Les organismes bénéficiaires souhaitant utiliser CommonLink doivent créer un compte et fournir les informations et justificatifs nécessaires à leur identification et validation.</p>
      <p>CommonLink peut valider, refuser, suspendre ou retirer un organisme bénéficiaire ou une campagne notamment en cas d'informations inexactes, de refus KYC/KYB, de soupçon de fraude ou de risque juridique.</p>

      <h2>16. Contenus publiés par les organismes bénéficiaires</h2>
      <p>L'organisme bénéficiaire accorde à CommonLink une licence non exclusive, gratuite et mondiale permettant d'héberger, reproduire et diffuser les contenus transmis pour les besoins du service.</p>

      <h2>17. Résiliation et suspension des organismes bénéficiaires</h2>
      <p>Le contrat entre CommonLink et l'organisme bénéficiaire est conclu pour une durée indéterminée. Chaque partie peut résilier avec un préavis de 30 jours, sous réserve du traitement des opérations en cours.</p>
      <p>CommonLink peut suspendre immédiatement l'accès d'un organisme bénéficiaire en cas de fraude, information fausse ou risque légal.</p>

      <h2>18. Obligations des utilisateurs</h2>
      <p>L'utilisateur s'engage à utiliser le Site et la Plateforme conformément à la loi et aux présentes conditions. Il est notamment interdit d'utiliser le Site à des fins frauduleuses, d'usurper une identité, de perturber le fonctionnement du Site ou de contourner les mesures de sécurité.</p>

      <h2>19. Propriété intellectuelle</h2>
      <p>Le Site, la Plateforme, la marque CommonLink et l'ensemble des contenus édités par CommonLink sont protégés par les règles applicables en matière de propriété intellectuelle. Toute reproduction ou exploitation non autorisée est interdite.</p>

      <h2>20. Données personnelles</h2>
      <p>Les informations relatives aux traitements de données personnelles sont décrites dans la <a href="/politique-confidentialite">Politique de confidentialité</a> accessible sur le Site.</p>
      <p>Pour exercer vos droits : <a href="mailto:legal@common-link.org">legal@common-link.org</a></p>

      <h2>21. Cookies</h2>
      <p>Les règles applicables sont décrites dans la <a href="/politique-cookies">Politique cookies</a> accessible sur le Site.</p>

      <h2>22. Disponibilité et sécurité</h2>
      <p>CommonLink met en œuvre des mesures raisonnables pour assurer le bon fonctionnement du Site mais ne garantit pas une disponibilité continue ou sans erreur.</p>

      <h2>23. Limitation de responsabilité</h2>
      <p>CommonLink ne garantit pas le succès d'une campagne ni l'usage final des fonds par l'organisme bénéficiaire. CommonLink ne saurait être tenue responsable des dommages indirects ou des dysfonctionnements imputables à des prestataires tiers.</p>

      <h2>24. Force majeure</h2>
      <p>CommonLink ne saurait être tenue responsable en cas d'inexécution résultant d'un cas de force majeure ou d'un événement échappant raisonnablement à son contrôle.</p>

      <h2>25. Contact</h2>
      <div className="contact-block">
        <a href="mailto:contact@common-link.org">contact@common-link.org</a>
      </div>
      <p>CommonLink s'efforce de répondre dans un délai de 48 heures ouvrées.</p>

      <h2>26. Droit applicable</h2>
      <p>Les présentes conditions générales sont soumises au droit français. Pour les litiges entre CommonLink et un organisme bénéficiaire professionnel, compétence est attribuée au tribunal de commerce d'Antibes, sauf règle impérative contraire.</p>
    </LegalContent>
  );
}
