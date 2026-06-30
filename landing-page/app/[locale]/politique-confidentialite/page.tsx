import { getTranslations } from 'next-intl/server';
import { LegalContent } from '@/components/layout/LegalContent';

export async function generateMetadata({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'metadata.privacy' });
  return {
    title: t('title'),
    description: t('description'),
  };
}

export default function PolitiqueConfidentialitePage() {
  return (
    <LegalContent title="Politique de confidentialité" lastUpdated="29 juin 2026">
      <p>
        CommonLink attache une importance particulière à la protection des données personnelles.
        La présente Politique de confidentialité explique quelles données personnelles sont collectées,
        pour quelles finalités, sur quelles bases juridiques, pendant combien de temps elles sont conservées,
        avec qui elles peuvent être partagées et quels droits peuvent être exercés.
      </p>

      <h2>1. Responsable de traitement</h2>
      <div className="contact-block">
        <strong>CommonLink</strong><br />
        SAS, société commerciale de l'économie sociale et solidaire<br />
        Siège social : 1047 Chemin des Impiniers, 06220 Vallauris, France<br />
        SIREN : 105 153 928<br />
        Email : <a href="mailto:legal@common-link.org">legal@common-link.org</a>
      </div>
      <p>Le référent données personnelles est : <strong>Julian Maiolino</strong></p>
      <p>CommonLink n'a pas désigné de délégué à la protection des données.</p>

      <h2>2. Données collectées</h2>
      <h3>2.1 Données des donateurs</h3>
      <p>CommonLink peut collecter notamment : prénom, nom, adresse email, adresse postale, pays de résidence, informations relatives au compte utilisateur, données relatives aux dons (date, montant, organisme, campagne, moyen de paiement), numéro d'ordre des reçus fiscaux, historique des dons, adresse IP, logs techniques, échanges avec le support.</p>
      <p>CommonLink ne collecte pas directement les données complètes de carte bancaire.</p>

      <h3>2.2 Données des organismes bénéficiaires</h3>
      <p>CommonLink peut collecter notamment : dénomination, forme juridique, adresse, numéro d'identification, informations relatives aux représentants, documents KYB, informations bancaires, données relatives aux campagnes et justificatifs d'utilisation des fonds.</p>

      <h3>2.3 Données techniques</h3>
      <p>CommonLink peut collecter : adresse IP, logs de connexion, données relatives au navigateur et au terminal, données de sécurité, données relatives aux cookies strictement nécessaires.</p>

      <h2>3. Finalités des traitements</h2>
      <p>Les données personnelles peuvent être traitées pour :</p>
      <ul>
        <li>création et gestion des comptes utilisateurs et organismes bénéficiaires ;</li>
        <li>présentation des campagnes et traitement des dons ;</li>
        <li>génération et transmission des reçus fiscaux ;</li>
        <li>mise à disposition d'informations de transparence et de preuve d'utilisation ;</li>
        <li>conformité KYC, KYB, lutte contre la fraude et obligations applicables ;</li>
        <li>gestion du support et des demandes de contact ;</li>
        <li>facturation des organismes bénéficiaires et tenue comptable ;</li>
        <li>respect des obligations légales et réglementaires ;</li>
        <li>sécurité du Site et prévention de la fraude ;</li>
        <li>prospection B2B auprès d'organismes susceptibles d'utiliser CommonLink ;</li>
        <li>envoi de newsletters aux personnes ayant donné leur consentement.</li>
      </ul>

      <h2>4. Bases juridiques</h2>
      <p>Les traitements reposent, selon les cas, sur :</p>
      <ul>
        <li>l'exécution d'un contrat ou de mesures précontractuelles ;</li>
        <li>le respect d'obligations légales ;</li>
        <li>l'intérêt légitime de CommonLink (sécurité, prévention de la fraude, prospection B2B) ;</li>
        <li>le consentement de la personne concernée (newsletter, certains cookies) ;</li>
        <li>la constatation, l'exercice ou la défense de droits en justice.</li>
      </ul>

      <h2>5. Destinataires des données</h2>
      <p>Les données personnelles peuvent être transmises, selon les cas, aux :</p>
      <ul>
        <li>équipes internes habilitées de CommonLink ;</li>
        <li>organismes bénéficiaires concernés par les dons ;</li>
        <li>prestataires de paiement et de monnaie électronique ;</li>
        <li>prestataires KYC ou KYB ;</li>
        <li>prestataires d'hébergement (Clever Cloud) ;</li>
        <li>prestataires techniques, d'email transactionnel et de support ;</li>
        <li>autorités administratives, judiciaires ou fiscales lorsque la loi l'exige.</li>
      </ul>
      <p>CommonLink peut utiliser les services de Monerium pour certaines fonctionnalités liées à la monnaie électronique et à la transparence des flux.</p>

      <h2>6. Transferts hors Union européenne</h2>
      <p>CommonLink privilégie des prestataires situés dans l'Union européenne. Au lancement, CommonLink n'a pas vocation à transférer les données personnelles hors de l'Union européenne.</p>

      <h2>7. Durées de conservation</h2>
      <table>
        <thead>
          <tr>
            <th>Catégorie de données</th>
            <th>Durée de conservation</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>Données de compte donateur</td>
            <td>Durée d'existence du compte, puis archivage si nécessaire</td>
          </tr>
          <tr>
            <td>Données relatives aux dons, reçus fiscaux et obligations comptables</td>
            <td>Jusqu'à 10 ans à compter de la clôture de l'exercice concerné</td>
          </tr>
          <tr>
            <td>Données relatives aux organismes bénéficiaires</td>
            <td>Durée de la relation contractuelle, puis archivage selon obligations légales</td>
          </tr>
          <tr>
            <td>Logs techniques et données de sécurité</td>
            <td>6 à 12 mois, sauf nécessité plus longue en cas d'incident</td>
          </tr>
          <tr>
            <td>Données de prospection B2B</td>
            <td>3 ans à compter du dernier contact actif</td>
          </tr>
          <tr>
            <td>Données newsletter</td>
            <td>Jusqu'au retrait du consentement ou 3 ans après le dernier contact</td>
          </tr>
          <tr>
            <td>Demandes relatives aux droits RGPD</td>
            <td>Durée nécessaire au traitement, puis archivage probatoire jusqu'à 5 ans</td>
          </tr>
        </tbody>
      </table>

      <h2>8. Newsletter et prospection</h2>
      <p>CommonLink peut envoyer une newsletter aux personnes ayant donné leur consentement (opt-in). Chaque email contient un lien de désinscription.</p>
      <p>CommonLink peut réaliser de la prospection B2B auprès d'organismes susceptibles d'être intéressés par ses services.</p>

      <h2>9. Cookies et mesure d'audience</h2>
      <p>Au lancement, CommonLink utilise uniquement les cookies strictement nécessaires au fonctionnement du Site. Pour plus d'informations, l'utilisateur peut consulter la <a href="/politique-cookies">Politique cookies</a>.</p>

      <h2>10. Sécurité</h2>
      <p>CommonLink met en œuvre des mesures techniques et organisationnelles raisonnables pour protéger les données personnelles : contrôle des accès, hébergement sécurisé, chiffrement, journalisation, séparation des rôles, limitation des accès aux personnes habilitées.</p>

      <h2>11. Droits des personnes</h2>
      <p>Conformément à la réglementation applicable, toute personne concernée peut exercer :</p>
      <ul>
        <li>droit d'accès ;</li>
        <li>droit de rectification ;</li>
        <li>droit d'effacement ;</li>
        <li>droit d'opposition ;</li>
        <li>droit à la limitation du traitement ;</li>
        <li>droit à la portabilité ;</li>
        <li>droit de retirer son consentement à tout moment ;</li>
        <li>droit de définir des directives relatives au sort de ses données après son décès.</li>
      </ul>
      <p>Ces droits peuvent être exercés en écrivant à : <a href="mailto:legal@common-link.org">legal@common-link.org</a></p>
      <p>CommonLink s'efforce de répondre dans un délai de 7 jours ouvrés.</p>

      <h2>12. Réclamation auprès de la CNIL</h2>
      <p>Si une personne estime que ses droits ne sont pas respectés, elle peut introduire une réclamation auprès de la <a href="https://www.cnil.fr" target="_blank" rel="noopener noreferrer">CNIL</a>.</p>

      <h2>13. Modification de la Politique de confidentialité</h2>
      <p>CommonLink peut modifier la présente Politique de confidentialité afin de tenir compte de l'évolution du Site, de la Plateforme, des traitements réalisés, des prestataires utilisés ou de la réglementation applicable. La version applicable est celle publiée sur le Site.</p>
    </LegalContent>
  );
}
