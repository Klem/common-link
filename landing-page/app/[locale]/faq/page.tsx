import { getTranslations } from 'next-intl/server';
import { LegalContent } from '@/components/layout/LegalContent';

export async function generateMetadata({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'metadata.faq' });
  return {
    title: t('title'),
    description: t('description'),
  };
}

interface FaqItem {
  q: string;
  a: React.ReactNode;
}

const faqs: FaqItem[] = [
  {
    q: "Qu'est-ce que CommonLink ?",
    a: "CommonLink est une plateforme de dons permettant aux associations, fondations, fonds de dotation, ONG et entreprises mécènes de collecter, suivre et documenter l'utilisation des fonds collectés, avec des mécanismes de transparence et de preuve d'utilisation.",
  },
  {
    q: "À qui s'adresse CommonLink ?",
    a: "CommonLink s'adresse notamment aux associations, fondations, fonds de dotation, ONG, entreprises mécènes, et aux donateurs souhaitant suivre l'utilisation des fonds. Le service est destiné au marché français au lancement.",
  },
  {
    q: "CommonLink est-elle une société de l'économie sociale et solidaire ?",
    a: "Oui. CommonLink est une société commerciale de l'économie sociale et solidaire. Une demande d'agrément ESUS a été déposée.",
  },
  {
    q: "CommonLink est-elle enregistrée à l'ORIAS ?",
    a: "CommonLink est en cours de démarche d'immatriculation auprès de l'ORIAS en qualité d'intermédiaire en financement participatif.",
  },
  {
    q: "CommonLink est-elle un établissement de paiement ?",
    a: "Non. CommonLink n'est pas un établissement de paiement. Les services de paiement sont fournis par des prestataires habilités.",
  },
  {
    q: "Comment fonctionne un don ?",
    a: "Le donateur crée un compte, sélectionne une campagne, réalise un don, reçoit une confirmation de paiement et peut accéder au suivi de son don depuis son compte. Lorsque les conditions applicables sont réunies, un reçu fiscal est généré par CommonLink pour le compte de l'organisme bénéficiaire.",
  },
  {
    q: "Peut-on donner sans créer de compte ?",
    a: "Non. La création d'un compte est nécessaire pour réaliser un don via CommonLink.",
  },
  {
    q: "Peut-on choisir précisément l'usage des fonds ?",
    a: "Non. Le donateur choisit une campagne, mais ne choisit pas une affectation individuelle détaillée des fonds. L'affectation est globale à la campagne sélectionnée.",
  },
  {
    q: "Les dons sont-ils ponctuels ou récurrents ?",
    a: "Les dons sont ponctuels au lancement. Des fonctionnalités de dons récurrents pourront être proposées ultérieurement.",
  },
  {
    q: "Quels moyens de paiement sont acceptés ?",
    a: "Au lancement, CommonLink prévoit d'accepter la carte bancaire et le prélèvement SEPA. D'autres moyens de paiement pourront être ajoutés ultérieurement.",
  },
  {
    q: "Le don est-il remboursable ?",
    a: (
      <>
        En principe, un don est définitif et non remboursable. Un remboursement peut toutefois être étudié en cas
        de double paiement, erreur manifeste, fraude, paiement non autorisé, campagne annulée ou organisme bénéficiaire
        retiré de la Plateforme. Les demandes doivent être adressées à{' '}
        <a href="mailto:contact@common-link.org">contact@common-link.org</a>.
      </>
    ),
  },
  {
    q: "Que se passe-t-il si une campagne n'atteint pas son objectif ?",
    a: "Sauf mention contraire, le don est juridiquement acquis à l'organisme bénéficiaire dès sa réception, indépendamment de l'atteinte de l'objectif affiché.",
  },
  {
    q: "Qui reçoit juridiquement le don ?",
    a: "Le don est réalisé directement au bénéfice de l'organisme bénéficiaire. CommonLink n'encaisse pas juridiquement les fonds pour son propre compte.",
  },
  {
    q: "CommonLink prend-elle une commission sur les dons ?",
    a: (
      <>
        CommonLink facture aux organismes bénéficiaires 8 % HT de frais de service et 2 % de frais de paiement et
        de traitement technique, facturés mensuellement. Le donateur peut également laisser une contribution
        volontaire facultative. Voir la <a href="/tarifs">page Tarifs</a> pour le détail.
      </>
    ),
  },
  {
    q: "Les reçus fiscaux sont-ils fournis ?",
    a: "Lorsque l'organisme bénéficiaire est éligible, les reçus fiscaux sont générés par CommonLink pour le compte de l'organisme bénéficiaire. Ils peuvent être transmis par email et/ou mis à disposition dans le compte du donateur.",
  },
  {
    q: "Le certificat numérique ou NFT est-il un reçu fiscal ?",
    a: "Non. Un certificat numérique, NFT ou élément de preuve complémentaire ne constitue pas un reçu fiscal. Le reçu fiscal est un document distinct, généré pour le compte de l'organisme bénéficiaire lorsque les conditions applicables sont réunies.",
  },
  {
    q: "CommonLink garantit-elle l'utilisation des fonds ?",
    a: "Non. CommonLink ne garantit pas l'usage final des fonds par l'organisme bénéficiaire. CommonLink met à disposition des outils de transparence permettant de suivre certains flux financiers et de présenter des preuves d'utilisation.",
  },
  {
    q: "Que signifie « preuve d'utilisation » ?",
    a: "La preuve d'utilisation désigne les éléments permettant de documenter l'usage des fonds : opérations enregistrées, justificatifs transmis, données techniques ou blockchain lorsque ces fonctionnalités sont activées. Ces éléments ne constituent pas une certification comptable ou juridique exhaustive.",
  },
  {
    q: "CommonLink utilise-t-elle la blockchain ?",
    a: "Certaines fonctionnalités de CommonLink peuvent utiliser des technologies blockchain, notamment Polygon, afin de contribuer à la transparence et à la documentation des flux.",
  },
  {
    q: "CommonLink utilise-t-elle Monerium ?",
    a: "Oui. CommonLink peut utiliser les services de Monerium pour certaines fonctionnalités liées à la monnaie électronique, à l'EURe et à la transparence des flux. Monerium est un établissement de monnaie électronique autorisé.",
  },
  {
    q: "CommonLink peut-elle refuser ou suspendre un organisme ?",
    a: "Oui. CommonLink peut refuser, suspendre ou retirer un organisme bénéficiaire ou une campagne notamment en cas d'informations inexactes, de refus KYC/KYB, de soupçon de fraude, de litige sérieux ou de risque juridique.",
  },
  {
    q: "CommonLink utilise-t-elle des cookies ?",
    a: (
      <>
        Au lancement, CommonLink utilise uniquement des cookies strictement nécessaires au fonctionnement du Site.
        Les cookies analytics, marketing ou publicitaires ne sont pas actifs au lancement.
        Voir la <a href="/politique-cookies">Politique cookies</a>.
      </>
    ),
  },
  {
    q: "Comment contacter CommonLink ?",
    a: (
      <>
        Email : <a href="mailto:contact@common-link.org">contact@common-link.org</a> — Téléphone : 06 31 63 60 08.
        CommonLink s&apos;efforce de répondre sous 48 heures ouvrées.
      </>
    ),
  },
  {
    q: "Comment exercer mes droits RGPD ?",
    a: (
      <>
        Pour exercer vos droits relatifs à vos données personnelles, écrivez à{' '}
        <a href="mailto:legal@common-link.org">legal@common-link.org</a>.
        CommonLink s&apos;efforce de répondre sous 7 jours ouvrés.
      </>
    ),
  },
];

export default function FAQPage() {
  return (
    <LegalContent title="FAQ">
      <p>Retrouvez ci-dessous les réponses aux questions les plus fréquentes sur CommonLink.</p>

      <div style={{ marginTop: '2rem' }}>
        {faqs.map((item, i) => (
          <details
            key={i}
            style={{ borderBottom: '1px solid var(--color-border-light)', paddingBottom: '0.5rem', marginBottom: '0.25rem' }}
          >
            <summary
              style={{
                padding: '1rem 0',
                cursor: 'pointer',
                fontFamily: 'var(--font-ui)',
                fontWeight: 600,
                fontSize: '0.95rem',
                color: 'var(--color-text-dark)',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                listStyle: 'none',
              }}
            >
              {item.q}
              <span style={{ color: 'var(--color-secondary)', fontSize: '1.2rem', marginLeft: '1rem', flexShrink: 0 }}>+</span>
            </summary>
            <div style={{ paddingBottom: '1rem' }}>
              <p>{item.a}</p>
            </div>
          </details>
        ))}
      </div>
    </LegalContent>
  );
}
