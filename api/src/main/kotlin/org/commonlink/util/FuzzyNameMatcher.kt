package org.commonlink.util

import org.apache.commons.codec.language.DoubleMetaphone
import org.apache.commons.text.similarity.JaroWinklerSimilarity
import org.apache.commons.text.similarity.LevenshteinDistance
import org.commonlink.entity.SanctionedNature
import java.text.Normalizer
import java.util.regex.Pattern

/**
 * Block-level phonetic + orthographic name matcher for LCB-FT asset-freeze screening.
 *
 *  1. Splits names into meaningful blocks, filtering particles ("DE", "AL", "VAN", …).
 *  2. Encodes each block with DoubleMetaphone (phonetic).
 *  3. Builds a cross-block similarity matrix and greedily picks best-matching pairs.
 *  4. Combines phonetic and orthographic scores as a weighted geometric mean.
 *
 * **Normalization contract** — both [query] and [stored] passed to [score] must already be
 * [NameNormalizer]-normalized (UPPERCASE ASCII). The caller is responsible for running
 * [transliterate] on the raw query string before [NameNormalizer.normalize], so that Cyrillic
 * and Arabic donor names are converted to Latin before storage-layer comparison.
 *
 * **Safety level** — mid-tier LCB-FT: substantially better than raw JaroWinkler (handles
 * inverted name order, transposition, particles, phonetic approximations, transliteration).
 * Below OFAC-grade: single source (DG Trésor only), no secondary-identifier scoring, no
 * nationality/country disambiguation, no re-screening on register updates.
 */
object FuzzyNameMatcher {

    private val CYRILLIC_PATTERN: Pattern = Pattern.compile(".*\\p{InCyrillic}.*")
    private val ARABIC_PATTERN:   Pattern = Pattern.compile(".*\\p{InArabic}.*")

    private val jaro        = JaroWinklerSimilarity()
    private val levenshtein = LevenshteinDistance()
    private val dm          = DoubleMetaphone().also { it.maxCodeLen = 64 }

    private val CONSONANTS = setOf('b','c','d','f','g','h','j','k','l','m','n','p','q','r','s','t','v','w','x','z')
    private val SCK         = setOf('s','c','k')

    // Uppercase, to match NameNormalizer-normalized blocks
    private val PARTICLES = setOf(
        "-", "AL", "DE", "LE", "VON", "VOM", "BIN", "BEN", "VAN", "DER",
        "EL", "IBN", "ABU", "DA", "DO", "DU", "LA", "DI", "DEL"
    )

    // Thresholds — from VerifierRunner constants
    private const val THRESHOLD_MONOBLOC_VS_MULTIBLOC_MATCH_FIRST_TERM = 0.6
    private const val THRESHOLD_LOWER_BOUND = 0.8
    private const val MALUS_SCORING_MONOBLOC_MULTIBLOC = 0.04

    // Phonetic score threshold for the greedy 3+-block loop
    private const val PHONETIC_THRESHOLD = 0.9

    // Cyrillic → Latin (GOST/BGN approximation for names)
    private val CYRILLIC_MAP: Map<Char, String> = mapOf(
        // lowercase
        'а' to "a",  'б' to "b",  'в' to "v",  'г' to "g",  'д' to "d",
        'е' to "e",  'ё' to "e",  'ж' to "zh", 'з' to "z",  'и' to "i",
        'й' to "y",  'к' to "k",  'л' to "l",  'м' to "m",  'н' to "n",
        'о' to "o",  'п' to "p",  'р' to "r",  'с' to "s",  'т' to "t",
        'у' to "u",  'ф' to "f",  'х' to "kh", 'ц' to "ts", 'ч' to "ch",
        'ш' to "sh", 'щ' to "shch",'ъ' to "",   'ы' to "y",  'ь' to "",
        'э' to "e",  'ю' to "yu", 'я' to "ya",
        // uppercase
        'А' to "a",  'Б' to "b",  'В' to "v",  'Г' to "g",  'Д' to "d",
        'Е' to "e",  'Ё' to "e",  'Ж' to "zh", 'З' to "z",  'И' to "i",
        'Й' to "y",  'К' to "k",  'Л' to "l",  'М' to "m",  'Н' to "n",
        'О' to "o",  'П' to "p",  'Р' to "r",  'С' to "s",  'Т' to "t",
        'У' to "u",  'Ф' to "f",  'Х' to "kh", 'Ц' to "ts", 'Ч' to "ch",
        'Ш' to "sh", 'Щ' to "shch",'Ъ' to "",   'Ы' to "y",  'Ь' to "",
        'Э' to "e",  'Ю' to "yu", 'Я' to "ya",
        // Ukrainian / Belarusian
        'і' to "i",  'ї' to "yi", 'є' to "ye", 'ґ' to "g",
        'І' to "i",  'Ї' to "yi", 'Є' to "ye", 'Ґ' to "g"
    )

    // Arabic → Latin (simplified for names)
    private val ARABIC_MAP: Map<Char, String> = mapOf(
        'ا' to "a",  'أ' to "a",  'إ' to "i",  'آ' to "a",  'ب' to "b",
        'ت' to "t",  'ث' to "th", 'ج' to "j",  'ح' to "h",  'خ' to "kh",
        'د' to "d",  'ذ' to "dh", 'ر' to "r",  'ز' to "z",  'س' to "s",
        'ش' to "sh", 'ص' to "s",  'ض' to "d",  'ط' to "t",  'ظ' to "z",
        'ع' to "a",  'غ' to "gh", 'ف' to "f",  'ق' to "q",  'ك' to "k",
        'ل' to "l",  'م' to "m",  'ن' to "n",  'ه' to "h",  'و' to "w",
        'ي' to "y",  'ى' to "a",  'ة' to "a",  'ء' to "",   'ؤ' to "u",
        'ئ' to "i",
        // Arabic-Indic digits
        '٠' to "0", '١' to "1", '٢' to "2", '٣' to "3", '٤' to "4",
        '٥' to "5", '٦' to "6", '٧' to "7", '٨' to "8", '٩' to "9"
    )

    // ── Public API ────────────────────────────────────────────────────────────────────────────

    /** True when [name] contains Cyrillic or Arabic characters. */
    fun containsCyrillicOrArabic(name: String): Boolean =
        CYRILLIC_PATTERN.matcher(name).matches() || ARABIC_PATTERN.matcher(name).matches()

    /**
     * Converts Cyrillic or Arabic characters in [raw] to their Latin approximation.
     * For Arabic, also inserts vowels between consecutive consonants to improve phonetic matching.
     * Non-Cyrillic/Arabic input is returned unchanged.
     */
    fun transliterate(raw: String): String {
        val hasCyrillic = CYRILLIC_PATTERN.matcher(raw).matches()
        val hasArabic   = ARABIC_PATTERN.matcher(raw).matches()
        if (!hasCyrillic && !hasArabic) return raw

        return if (hasCyrillic) {
            val sb = StringBuilder(raw.length * 2)
            for (c in raw) sb.append(CYRILLIC_MAP[c] ?: c.toString())
            removeAccents(sb.toString())
        } else {
            // Arabic: skip tashkeel diacritics (U+064B–U+065F), then insert vowels
            val sb = StringBuilder(raw.length * 2)
            for (c in raw) {
                when {
                    ARABIC_MAP.containsKey(c)  -> sb.append(ARABIC_MAP[c])
                    c.code in 0x064B..0x065F   -> { /* skip tashkeel */ }
                    else                        -> sb.append(c)
                }
            }
            removeAccents(insertArabicVowels(sb.toString()))
        }
    }

    /**
     * Scores the phonetic + orthographic similarity between two name strings, both already
     * normalized via [NameNormalizer].
     *
     * @param query         Query name (NameNormalizer-normalized, after transliteration).
     * @param stored        Stored register variant (NameNormalizer-normalized at ingestion).
     * @param nature        Determines weighting: PHYSICAL_PERSON is more orthographic,
     *                      LEGAL_ENTITY/VESSEL allows single-block vs multi-block comparison.
     * @param transliterated True when the original raw query had Cyrillic or Arabic characters,
     *                       which increases phonetic weight since transliteration is approximate.
     * @return Similarity in [0.0, 1.0]; higher means more similar.
     */
    fun score(
        query: String,
        stored: String,
        nature: SanctionedNature,
        transliterated: Boolean = false,
    ): Double {
        if (query.isBlank() || stored.isBlank()) return 0.0

        // Early-exit: length difference too large
        val lenDiff = Math.abs(query.length - stored.length)
        if (lenDiff > maxOf(query.length, stored.length) / 2 + 3) return 0.0

        val isEntity = nature != SanctionedNature.PHYSICAL_PERSON
        val ratio = when {
            transliterated -> if (isEntity) 0.7 else 0.9   // favor phonetic when transliteration was needed
            isEntity       -> 0.65
            else           -> 0.8
        }
        val allowMonoblocManyBloc = isEntity

        val blocks1 = getBlocks(query)
        val blocks2 = getBlocks(stored)
        if (blocks1.isEmpty() || blocks2.isEmpty()) return 0.0

        val dm1 = Array(blocks1.size) { dm.doubleMetaphone(blocks1[it]) ?: "" }
        val dm2 = Array(blocks2.size) { dm.doubleMetaphone(blocks2[it]) ?: "" }

        val phoneticMatrix = Array(blocks1.size) { i ->
            DoubleArray(blocks2.size) { j ->
                val l = dm1[i]; val r = dm2[j]
                if (l.isBlank() || r.isBlank()) 0.0 else jaro.apply(l, r)
            }
        }

        val monoBlocCompare = blocks1.size == 1 || blocks2.size == 1
        val monoBlocVsMulti = monoBlocCompare && blocks1.size != blocks2.size

        val scorePhonetic: Double
        val scoreOrtho: Double

        when {
            monoBlocCompare -> {
                if (blocks1.size == 1 && blocks2.size == 1 || allowMonoblocManyBloc) {
                    val pos = maxScore(phoneticMatrix)
                    scorePhonetic = phoneticMatrix[pos[0]][pos[1]]
                    scoreOrtho    = jaro.apply(blocks1[pos[0]], blocks2[pos[1]])

                    // For 1-vs-N: verify the single block actually matches the first token of the
                    // multi-block name — matching a middle token only is insufficient.
                    if (blocks1.size != 1 || blocks2.size != 1) {
                        val firstJ  = maxScoreFirstAxis(phoneticMatrix)
                        val secondI = maxScoreSecondAxis(phoneticMatrix)
                        val scoreFirst  = jaro.apply(blocks1[0], blocks2[firstJ])
                        val scoreSecond = jaro.apply(blocks1[secondI], blocks2[0])
                        if (scoreFirst  < THRESHOLD_MONOBLOC_VS_MULTIBLOC_MATCH_FIRST_TERM
                            || scoreSecond < THRESHOLD_MONOBLOC_VS_MULTIBLOC_MATCH_FIRST_TERM) {
                            return 0.0
                        }
                    }
                } else {
                    return 0.0
                }
            }

            minOf(blocks1.size, blocks2.size) == 2 -> {
                val pos1 = maxScore(phoneticMatrix)
                val s1p = phoneticMatrix[pos1[0]][pos1[1]]
                val s1o = jaro.apply(blocks1[pos1[0]], blocks2[pos1[1]])
                zeroRowAndCol(phoneticMatrix, pos1[0], pos1[1])

                val pos2 = maxScore(phoneticMatrix)
                val s2p = phoneticMatrix[pos2[0]][pos2[1]]
                val s2o = jaro.apply(blocks1[pos2[0]], blocks2[pos2[1]])

                scorePhonetic = Math.sqrt(s1p * s2p)
                scoreOrtho    = Math.sqrt(s1o * s2o)
            }

            else -> {
                // 3+-block greedy matching
                val minBlocs = minOf(blocks1.size, blocks2.size)
                var phoneticAccum = 1.0
                var orthoAccum    = 1.0
                var nbBlocks      = 0

                var maxTmp   = maxScore(phoneticMatrix)
                var phonTmp  = phoneticMatrix[maxTmp[0]][maxTmp[1]]
                var orthoTmp = jaro.apply(blocks1[maxTmp[0]], blocks2[maxTmp[1]])

                while (phonTmp > PHONETIC_THRESHOLD || nbBlocks < minBlocs) {
                    zeroRowAndCol(phoneticMatrix, maxTmp[0], maxTmp[1])
                    nbBlocks++
                    phoneticAccum *= phonTmp
                    orthoAccum    *= orthoTmp

                    maxTmp  = maxScore(phoneticMatrix)
                    phonTmp = phoneticMatrix[maxTmp[0]][maxTmp[1]]
                    if (phonTmp == 0.0) break   // matrix exhausted — avoids multiplying by zero
                    orthoTmp = jaro.apply(blocks1[maxTmp[0]], blocks2[maxTmp[1]])
                }

                if (nbBlocks == 0) return 0.0
                scorePhonetic = Math.pow(phoneticAccum, 1.0 / nbBlocks)
                scoreOrtho    = Math.pow(orthoAccum,    1.0 / nbBlocks)
            }
        }

        var total = Math.pow(scorePhonetic, ratio) * Math.pow(scoreOrtho, 1.0 - ratio)

        if (monoBlocVsMulti && allowMonoblocManyBloc) {
            total -= MALUS_SCORING_MONOBLOC_MULTIBLOC
        }

        // Levenshtein fine-grained penalty when near the threshold and lengths differ
        if (total >= THRESHOLD_LOWER_BOUND && query.length != stored.length) {
            val lev = levenshtein.apply(query, stored) ?: 0
            total -= lev / 1000.0
        }

        return maxOf(0.0, total)
    }

    // ── Private helpers ───────────────────────────────────────────────────────────────────────

    private fun removeAccents(input: String): String =
        Normalizer.normalize(input, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    private fun insertArabicVowels(text: String): String {
        if (text.length < 2) return text
        val indexes = mutableListOf<Int>()
        var prev = text[0]
        for (i in 1 until text.length) {
            val cur = text[i]
            if (prev != cur && prev in CONSONANTS && cur in CONSONANTS && !(prev in SCK && cur == 'h')) {
                indexes.add(i)
            }
            prev = cur
        }
        if (indexes.isEmpty()) return text
        val sb = StringBuilder()
        var last = 0
        for (idx in indexes) {
            sb.append(text, last, idx).append('a')
            last = idx
        }
        return sb.append(text.substring(last)).toString()
    }

    private fun getBlocks(normalized: String): Array<String> {
        if (normalized.isBlank()) return emptyArray()
        val raw = normalized.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (raw.size == 1) return raw.toTypedArray()
        val filtered = raw.filter { it !in PARTICLES }
        return if (filtered.isEmpty()) raw.toTypedArray() else filtered.toTypedArray()
    }

    private fun maxScore(matrix: Array<DoubleArray>): IntArray {
        var iMax = 0; var jMax = 0
        for (i in matrix.indices)
            for (j in matrix[i].indices)
                if (matrix[i][j] > matrix[iMax][jMax]) { iMax = i; jMax = j }
        return intArrayOf(iMax, jMax)
    }

    // Best column index in row 0
    private fun maxScoreFirstAxis(matrix: Array<DoubleArray>): Int {
        var jMax = 0
        for (j in matrix[0].indices) if (matrix[0][j] > matrix[0][jMax]) jMax = j
        return jMax
    }

    // Best row index in column 0
    private fun maxScoreSecondAxis(matrix: Array<DoubleArray>): Int {
        var iMax = 0
        for (i in matrix.indices) if (matrix[i][0] > matrix[iMax][0]) iMax = i
        return iMax
    }

    private fun zeroRowAndCol(matrix: Array<DoubleArray>, row: Int, col: Int) {
        for (j in matrix[row].indices) matrix[row][j] = 0.0
        for (i in matrix.indices) matrix[i][col] = 0.0
    }
}
