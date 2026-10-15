package org.commonlink.entity

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Diffusion method an association publishes its donation page with, as picked in the settings
 * "Diffusion" tab.
 *
 * The JSON values are the lowercase wire values of the frontend `EmbedType` (`app/src/lib/gtm.ts`),
 * the same ones sent to GTM as `embed_type`, so one vocabulary covers both. Stored as the entry
 * name, which must stay identical to the `chk_association_embed_type` CHECK constraint (migration V86).
 */
enum class EmbedType {
    /** Landing page hosted by CommonLink, shared as a plain link. */
    @JsonProperty("direct") DIRECT,

    /** Full landing page embedded on the association's site by `landing.js` (auto height). */
    @JsonProperty("script") SCRIPT,

    /** Full landing page in a fixed-height `<iframe>`, plus the GTM bridge script. */
    @JsonProperty("iframe") IFRAME,

    /** Standalone HTML export of the landing page, hosted by the association. */
    @JsonProperty("html") HTML,

    /** Donation form only, embedded by `widget.js`. */
    @JsonProperty("widget") WIDGET,
}
