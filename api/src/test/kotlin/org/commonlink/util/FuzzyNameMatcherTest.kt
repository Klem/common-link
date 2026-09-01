package org.commonlink.util

import org.commonlink.entity.SanctionedNature
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Calibration tests for [FuzzyNameMatcher]'s block-coverage malus.
 *
 * The pairs below are not fictional: they are real false-positive correspondences produced
 * during manual donation-widget testing on 2026-08-28 (see `freeze_screening_match` rows,
 * `audit_log_seq_ref` 195/197/200 for the donor pairs, and an earlier association-onboarding
 * test for the "TECHNO" pairs). Register-side values are taken from the real
 * `sanctioned_entity.normalized_names` variant that actually produced each recorded score —
 * not the entry's display name, which is often a different (and longer) stored variant.
 *
 * All persons and entities named here are either real DG Trésor register entries (public
 * information) or values typed by CommonLink staff during testing — no real donor or
 * association identity is involved.
 *
 * ### What this malus does and does not fix
 * It only removes the advantage a register entry gets from having *more blocks than the
 * query* (extra blocks are ignored today, at no cost). It does **not** touch a same-block-count
 * comparison: two 2-block names that are simply phonetically close (see the `MAKHLOUF Rami`
 * case below, still 0.88 after this fix) are a different problem — reducing that would need a
 * higher `score-threshold`, a change deliberately left out of this commit pending a real
 * adversarial true-positive dataset (see `.tasks/todo.md`). Similarly, a single-block query
 * colliding with a single-block register *alias* (`MAG` vs `MIKANO`) is unaffected by this
 * malus — that class of input is closed upstream instead, by the donor full-name minimum of two
 * words (`CreateGuestDonationRequest.donorFullName`, `donationSchema.ts`), which now makes a
 * bare 3-letter query impossible to submit in the first place.
 */
class FuzzyNameMatcherTest {

    private fun score(query: String, stored: String, nature: SanctionedNature): Double =
        FuzzyNameMatcher.score(NameNormalizer.normalize(query), NameNormalizer.normalize(stored), nature)

    // ── Real false positives — donor screening (2026-08-28) ──────────────────────────────────

    @Test
    fun `MAGALI RAMISSE vs 4-block register entry DOMINGUEZ RAMIREZ is no longer favored by extra blocks`() {
        val twoBlock  = score("MAGALI RAMISSE", "MAKHLOUF Rami", SanctionedNature.PHYSICAL_PERSON)
        val fourBlock = score("MAGALI RAMISSE", "DOMÍNGUEZ RAMÍREZ José Miguel", SanctionedNature.PHYSICAL_PERSON)
        assertTrue(fourBlock < twoBlock,
            "A 4-block entry ($fourBlock) must no longer outscore a 2-block entry ($twoBlock) " +
                "purely because it has more unmatched blocks to ignore")
        assertTrue(fourBlock < 0.85, "Expected below threshold, was $fourBlock")
        // MAKHLOUF Rami is a genuine same-block-count phonetic collision (RAMI is a near-exact
        // prefix of RAMISSE) — the coverage malus does not apply here (0 unmatched blocks) and
        // this pair deliberately stays >= 0.85. Fixing it is a threshold decision, not a coverage
        // one: see the KDoc above and .tasks/todo.md.
        assertTrue(twoBlock >= 0.85, "Documents current (unfixed) behavior, was $twoBlock")
    }

    @Test
    fun `single-block query vs single-block register alias is unaffected by the coverage malus`() {
        // Real production hits: "MAG" (a 3-letter donor input, since blocked by the two-word
        // minimum on donorFullName) against the short aliases actually carried by these two
        // register entries — not their display names, which are much longer and never entered
        // the comparison. Mono-bloc vs mono-bloc has 0 unmatched blocks: this malus cannot and
        // should not touch it.
        val vsAltesAlias  = score("MAG", "MIKANO", SanctionedNature.PHYSICAL_PERSON)   // alias of ALTÈS Micanor
        val vsLumisaAlias = score("MAG", "MUKADE", SanctionedNature.PHYSICAL_PERSON)   // alias of LUMISA Muhamad
        assertTrue(vsAltesAlias >= 0.85, "Documents current (unfixed) behavior, was $vsAltesAlias")
        assertTrue(vsLumisaAlias >= 0.85, "Documents current (unfixed) behavior, was $vsLumisaAlias")
    }

    // ── Real false positives — association screening (earlier onboarding test) ───────────────

    @Test
    fun `TECHNO mono-bloc query does not match unrelated multi-block entity entries`() {
        val vsLlcFinist = score("TECHNO", "LLC Finist", SanctionedNature.LEGAL_ENTITY)
        val vsPositive  = score("TECHNO", "Positive Group PJSC", SanctionedNature.LEGAL_ENTITY)
        assertTrue(vsLlcFinist < 0.85, "Expected below threshold, was $vsLlcFinist")
        assertTrue(vsPositive < 0.85, "Expected below threshold, was $vsPositive")
    }

    @Test
    fun `TECHNO still matches its true near-exact prefix TECHNOLAB`() {
        // Same query, same nature, one extra unmatched block — must stay a legitimate hit:
        // the coverage malus must not blind the matcher to a genuine substring/prefix homonym.
        val vsTechnolab = score("TECHNO", "TECHNOLAB", SanctionedNature.LEGAL_ENTITY)
        assertTrue(vsTechnolab >= 0.85, "Expected at or above threshold, was $vsTechnolab")
    }

    // ── Regression — existing true positives must not be weakened by the malus ───────────────

    @Test
    fun `exact match is unaffected by the coverage malus`() {
        val exact = score("FICTIVUS Alexius", "FICTIVUS Alexius", SanctionedNature.PHYSICAL_PERSON)
        assertTrue(exact >= 0.99, "Exact match should stay ~1.0, was $exact")
    }

    @Test
    fun `reversed PRENOM NOM order still matches — equal block count, no coverage malus applies`() {
        val reversed = score("FICTIVUS Alexius", "Alexius FICTIVUS", SanctionedNature.PHYSICAL_PERSON)
        assertTrue(reversed >= 0.85, "Expected reversed-order match to stay at or above threshold, was $reversed")
    }

    @Test
    fun `particle-filtered blocks still match — DE is dropped from both sides before counting`() {
        val particleFiltered = score("EXEMPLA Janus", "DE EXEMPLA Janus", SanctionedNature.PHYSICAL_PERSON)
        assertTrue(particleFiltered >= 0.85,
            "Expected particle-filtered match to stay at or above threshold, was $particleFiltered")
    }
}
