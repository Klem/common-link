import { getTranslations } from 'next-intl/server';
import { LegalContent } from '@/components/layout/LegalContent';

export async function generateMetadata({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'metadata.contact' });
  return {
    title: t('title'),
    description: t('description'),
  };
}

export default function ContactPage() {
  return (
    <LegalContent title="Contact">
      <p>
        Vous pouvez contacter CommonLink pour toute question relative au Site, à la Plateforme,
        aux dons, aux organismes bénéficiaires, aux partenariats ou aux demandes légales.
      </p>

      <h2>Contact général</h2>
      <div className="contact-block">
        Email : <a href="mailto:contact@common-link.org">contact@common-link.org</a><br />
        Téléphone : 06 31 63 60 08
      </div>
      <p>CommonLink s'efforce de répondre aux demandes dans un délai de 48 heures ouvrées.</p>

      <h2>Demandes légales</h2>
      <p>Pour toute demande juridique, administrative ou relative aux mentions légales :</p>
      <div className="contact-block">
        <a href="mailto:legal@common-link.org">legal@common-link.org</a>
      </div>

      <h2>Données personnelles</h2>
      <p>Pour exercer vos droits relatifs à vos données personnelles :</p>
      <div className="contact-block">
        <a href="mailto:legal@common-link.org">legal@common-link.org</a>
      </div>
      <p>
        CommonLink s'efforce de répondre dans un délai de 7 jours ouvrés. En tout état de cause,
        CommonLink répond dans les délais prévus par la réglementation applicable.
      </p>

      <h2>Adresse postale</h2>
      <div className="contact-block">
        CommonLink<br />
        1047 Chemin des Impiniers<br />
        06220 Vallauris<br />
        France
      </div>

      <h2>Informations société</h2>
      <div className="contact-block">
        <strong>CommonLink</strong><br />
        SAS, société commerciale de l'économie sociale et solidaire<br />
        Capital social : 10 000 €<br />
        SIREN : 105 153 928<br />
        RCS : Antibes<br />
        TVA intracommunautaire : FR45105153928
      </div>
    </LegalContent>
  );
}
