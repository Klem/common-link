import { getTranslations } from 'next-intl/server';
import { LegalContent } from '@/components/layout/LegalContent';

export async function generateMetadata({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'metadata.tarifs' });
  return {
    title: t('title'),
    description: t('description'),
  };
}

export default function TarifsPage() {
  return (
    <LegalContent title="Tarifs" lastUpdated="29 juin 2026">
      <p>
        CommonLink applique une tarification transparente aux organismes bénéficiaires utilisant la Plateforme
        pour collecter, suivre et documenter l'utilisation des dons.
      </p>

      <h2>1. Donateurs</h2>
      <p>L'utilisation de CommonLink est <strong>gratuite pour les donateurs</strong>.</p>
      <p>
        Lors d'un don, le donateur peut laisser une contribution volontaire à CommonLink.
        Cette contribution est facultative. Elle peut être suggérée dynamiquement ou préremplie,
        mais le donateur peut la modifier ou la ramener à zéro avant validation du paiement.
        La contribution volontaire est exprimée toutes taxes comprises.
      </p>

      <h2>2. Organismes bénéficiaires</h2>
      <p>CommonLink facture aux organismes bénéficiaires des frais de service correspondant à :</p>
      <p><strong>8 % HT du montant des dons collectés</strong></p>
      <p>
        La TVA applicable est ajoutée à ces frais. Ces frais sont supportés par l'organisme bénéficiaire.
        Ils ne sont pas prélevés sur le don brut au moment du paiement du donateur.
        Ils font l'objet d'une facturation mensuelle.
      </p>

      <h2>3. Frais de paiement et frais techniques</h2>
      <p>Des frais de paiement et de traitement technique correspondant à :</p>
      <p><strong>2 % du montant des dons collectés</strong></p>
      <p>sont également facturés à l'organisme bénéficiaire. Ces frais couvrent notamment :</p>
      <ul>
        <li>le traitement des paiements ;</li>
        <li>les infrastructures techniques ;</li>
        <li>les opérations nécessaires au fonctionnement de la Plateforme ;</li>
        <li>le cas échéant, certaines opérations liées à la transparence ou aux technologies blockchain.</li>
      </ul>

      <h2>4. Exemple indicatif</h2>
      <p>Pour 100 € de dons collectés par un organisme bénéficiaire :</p>
      <table>
        <thead>
          <tr>
            <th>Élément</th>
            <th style={{ textAlign: 'right' }}>Montant</th>
          </tr>
        </thead>
        <tbody>
          <tr><td>Dons collectés</td><td style={{ textAlign: 'right' }}>100,00 €</td></tr>
          <tr><td>Frais de service CommonLink</td><td style={{ textAlign: 'right' }}>8,00 € HT</td></tr>
          <tr><td>TVA sur frais de service</td><td style={{ textAlign: 'right' }}>1,60 €</td></tr>
          <tr><td>Frais de paiement et techniques</td><td style={{ textAlign: 'right' }}>2,00 €</td></tr>
          <tr><td><strong>Total facturé à l'organisme bénéficiaire</strong></td><td style={{ textAlign: 'right' }}><strong>11,60 € TTC</strong></td></tr>
        </tbody>
      </table>
      <p>Cet exemple est indicatif et ne tient pas compte d'éventuelles conditions particulières ou négociées.</p>

      <h2>5. Offre de visibilité gratuite</h2>
      <p>
        CommonLink peut proposer une offre gratuite permettant à certains organismes de bénéficier d'une visibilité
        sur la Plateforme. Cette offre gratuite ne permet pas de recevoir des dons via CommonLink.
      </p>

      <h2>6. Conditions spécifiques</h2>
      <p>
        CommonLink peut proposer des conditions tarifaires spécifiques ou négociées à certains organismes bénéficiaires,
        notamment selon leur profil, leur volume de collecte, leur statut, leur usage de la Plateforme ou un accord
        contractuel spécifique.
      </p>

      <h2>7. Aucun frais caché</h2>
      <p>
        CommonLink ne facture aucun frais caché.
        Les frais applicables aux organismes bénéficiaires sont indiqués sur cette page ou dans les conditions
        contractuelles applicables.
      </p>

      <h2>8. Facturation</h2>
      <p>
        Les frais dus par les organismes bénéficiaires sont facturés mensuellement, après collecte des dons.
        Les factures sont émises par CommonLink et transmises à l'organisme bénéficiaire selon les modalités convenues.
      </p>
    </LegalContent>
  );
}
