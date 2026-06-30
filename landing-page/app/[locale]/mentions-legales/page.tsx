import { getTranslations } from 'next-intl/server';
import { LegalContent } from '@/components/layout/LegalContent';

export async function generateMetadata({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'metadata.mentionsLegales' });
  return {
    title: t('title'),
    description: t('description'),
  };
}

export default function MentionsLegalesPage() {
  return (
    <LegalContent title="Mentions légales" lastUpdated="29 juin 2026">
      <p>
        Le présent site internet, accessible à l'adresse{' '}
        <a href="https://www.common-link.org/">https://www.common-link.org/</a>, est édité par CommonLink.
      </p>

      <h2>1. Éditeur du site</h2>
      <div className="contact-block">
        <strong>CommonLink</strong><br />
        Société par actions simplifiée (SAS), société commerciale de l'économie sociale et solidaire (ESS)<br />
        Capital social : 10 000 €<br />
        Siège social : 1047 Chemin des Impiniers, 06220 Vallauris, France<br />
        SIREN : 105 153 928<br />
        SIRET : 105 153 928 00012<br />
        RCS : Antibes<br />
        Numéro de TVA intracommunautaire : FR45105153928<br />
        Code NAF / APE : 6201Z
      </div>
      <p>
        CommonLink est une société commerciale de l'économie sociale et solidaire. Une demande d'agrément ESUS a été déposée.
      </p>
      <p>
        CommonLink est en cours de démarche d'immatriculation auprès de l'ORIAS en qualité d'intermédiaire en financement participatif.
      </p>

      <h2>2. Directeur de la publication</h2>
      <p>Le directeur de la publication est :<br /><strong>Julian Maiolino</strong>, Président de CommonLink</p>

      <h2>3. Responsable éditorial</h2>
      <p>Le responsable du contenu éditorial du site est : <strong>Julian Maiolino</strong></p>

      <h2>4. Contact</h2>
      <div className="contact-block">
        Email : <a href="mailto:contact@common-link.org">contact@common-link.org</a><br />
        Téléphone : 06 31 63 60 08<br />
        Email pour les demandes légales : <a href="mailto:legal@common-link.org">legal@common-link.org</a>
      </div>
      <p>CommonLink s'efforce de répondre aux demandes reçues dans un délai de 48 heures ouvrées.</p>

      <h2>5. Hébergement</h2>
      <div className="contact-block">
        <strong>Clever Cloud</strong><br />
        Société par actions simplifiée (SAS)<br />
        4 rue Voltaire, 44000 Nantes, France<br />
        Site internet : <a href="https://www.clever.cloud/">https://www.clever.cloud/</a>
      </div>

      <h2>6. Nom de domaine</h2>
      <p>Le nom de domaine common-link.org est géré techniquement auprès d'OVH.</p>

      <h2>7. Propriété intellectuelle</h2>
      <p>
        L'ensemble des éléments présents sur le site, notamment les textes, logos, marques, éléments graphiques,
        interfaces, bases de données, images, vidéos, icônes, codes, structures, dénominations et contenus, sont
        protégés par les dispositions applicables en matière de propriété intellectuelle.
      </p>
      <p>
        Toute reproduction, représentation, modification, adaptation, extraction, diffusion ou exploitation,
        totale ou partielle, du site ou de ses éléments, sans autorisation préalable écrite de CommonLink, est interdite.
      </p>

      <h2>8. Responsabilité</h2>
      <p>
        CommonLink s'efforce de fournir sur le site des informations exactes, à jour et accessibles. Toutefois,
        CommonLink ne peut garantir l'absence d'erreur, d'omission, d'interruption ou d'indisponibilité temporaire du site.
      </p>
      <p>
        Les informations présentées sur le site ont une finalité générale de présentation du service CommonLink.
        Elles ne constituent pas un conseil juridique, fiscal, financier ou comptable personnalisé.
      </p>

      <h2>9. Données personnelles</h2>
      <p>
        Les traitements de données personnelles réalisés par CommonLink sont décrits dans la{' '}
        <a href="/politique-confidentialite">Politique de confidentialité</a> accessible sur le site.
      </p>
      <p>Pour toute demande relative aux données personnelles : <a href="mailto:legal@common-link.org">legal@common-link.org</a></p>

      <h2>10. Cookies</h2>
      <p>
        Les règles applicables aux cookies et autres traceurs sont décrites dans la{' '}
        <a href="/politique-cookies">Politique cookies</a> accessible sur le site.
      </p>
    </LegalContent>
  );
}
