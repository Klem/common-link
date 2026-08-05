package org.commonlink.entity

/**
 * Visual theme of an association's donation landing page.
 *
 * Each entry maps to a CSS block overriding the `--lp-*` design tokens on `.lp-root`
 * (`app/src/app/[locale]/lp/[widgetToken]/landing.css`). Associations pick a theme from a
 * closed list of named palettes — free colour input is deliberately not offered, so contrast
 * and legibility are guaranteed by construction for non-technical users.
 *
 * Entries must stay identical to the `chk_association_landing_theme` CHECK constraint
 * (migration V48).
 */
enum class LandingTheme {
    /** Current default palette (warm orange + teal). Rendering is unchanged from before V48. */
    DEFAULT,
    WARM,
    TRUST,
    NATURE,
    SOBER,
}
