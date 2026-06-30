import { getTranslations } from 'next-intl/server';
import { LegalContent } from '@/components/layout/LegalContent';

export async function generateMetadata({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'metadata.cookies' });
  return {
    title: t('title'),
    description: t('description'),
  };
}

export default function PolitiqueCookiesPage() {
  return (
    <LegalContent title="Politique cookies" lastUpdated="29 juin 2026">
      <p>
        La présente Politique cookies explique comment CommonLink utilise les cookies et autres traceurs
        sur le site <a href="https://www.common-link.org/">https://www.common-link.org/</a>.
      </p>

      <h2>1. Qu'est-ce qu'un cookie ?</h2>
      <p>
        Un cookie est un petit fichier déposé ou lu sur le terminal de l'utilisateur lorsqu'il consulte un site internet.
        Les cookies peuvent avoir différentes finalités : permettre le bon fonctionnement d'un site, sécuriser une session,
        mémoriser des préférences, mesurer l'audience ou personnaliser des contenus.
      </p>

      <h2>2. Cookies utilisés au lancement</h2>
      <p>Au lancement, CommonLink utilise uniquement des cookies <strong>strictement nécessaires</strong> au fonctionnement du Site.</p>
      <p>Ces cookies peuvent être nécessaires pour :</p>
      <ul>
        <li>permettre l'accès au Site ;</li>
        <li>assurer la sécurité du Site ;</li>
        <li>maintenir une session utilisateur ;</li>
        <li>mémoriser certaines préférences strictement nécessaires ;</li>
        <li>prévenir les abus ou accès non autorisés ;</li>
        <li>assurer le bon fonctionnement technique de la Plateforme.</li>
      </ul>
      <p>Ces cookies ne nécessitent pas le consentement préalable de l'utilisateur lorsqu'ils sont strictement nécessaires au service demandé.</p>

      <h2>3. Cookies non utilisés au lancement</h2>
      <p>Au lancement, CommonLink n'utilise pas :</p>
      <ul>
        <li>Google Analytics ;</li>
        <li>cookies de mesure d'audience non exemptés ;</li>
        <li>cookies marketing ;</li>
        <li>pixels publicitaires ;</li>
        <li>retargeting ;</li>
        <li>cookies de réseaux sociaux ;</li>
        <li>outils de suivi comportemental ;</li>
        <li>outils d'enregistrement de session ;</li>
        <li>outils de heatmap.</li>
      </ul>
      <p>
        Ces outils pourront être activés ultérieurement uniquement après information des utilisateurs et mise en place
        d'un mécanisme de consentement conforme lorsque celui-ci est requis.
      </p>

      <h2>4. Cookies de paiement</h2>
      <p>
        Certains prestataires de paiement peuvent utiliser des cookies ou traceurs nécessaires au traitement sécurisé des paiements.
        Lorsque ces cookies sont strictement nécessaires à l'exécution du paiement demandé par l'utilisateur, ils peuvent être
        déposés sans consentement préalable.
      </p>

      <h2>5. Consentement</h2>
      <p>
        Si CommonLink active ultérieurement des cookies analytics, marketing ou de personnalisation nécessitant le consentement,
        l'utilisateur pourra accepter, refuser ou paramétrer ses choix, et retirer son consentement à tout moment.
        CommonLink mettra alors à disposition un outil de gestion du consentement accessible depuis le Site.
      </p>

      <h2>6. Durée de conservation</h2>
      <p>
        Les cookies strictement nécessaires sont conservés pour une durée limitée à ce qui est nécessaire à leur finalité.
        Les durées précises peuvent varier selon le type de cookie utilisé.
      </p>

      <h2>7. Gestion des cookies depuis le navigateur</h2>
      <p>
        L'utilisateur peut configurer son navigateur pour bloquer ou supprimer certains cookies.
        La désactivation de cookies strictement nécessaires peut toutefois empêcher le bon fonctionnement du Site ou
        de certaines fonctionnalités de la Plateforme.
      </p>

      <h2>8. Modification de la Politique cookies</h2>
      <p>
        CommonLink peut modifier la présente Politique cookies afin de tenir compte de l'évolution du Site, de la Plateforme,
        des outils utilisés ou de la réglementation applicable. La version applicable est celle publiée sur le Site.
      </p>
    </LegalContent>
  );
}
