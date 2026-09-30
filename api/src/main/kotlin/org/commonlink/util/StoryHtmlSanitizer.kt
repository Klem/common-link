package org.commonlink.util

import org.jsoup.Jsoup
import org.jsoup.safety.Safelist

/**
 * Strips a campaign story's rich-text HTML down to a small, safe tag set before it is ever
 * persisted -- association-authored content, rendered donor-side with `dangerouslySetInnerHTML`,
 * so sanitizing only at render time would leave a stored-XSS payload sitting in the database.
 *
 * `<script>`, event handler attributes (`onerror`, `onclick`, ...), `style`, `iframe` and any
 * `javascript:` URL on a link are always stripped -- they are simply not in the safelist.
 *
 * `img[src]` is deliberately left without protocol validation: story images are served from our
 * own *relative* path (`/api/public/campaigns/{id}/story-images/{imageId}`), and jsoup's
 * protocol/relative-link resolution (which needs a base URI to make sense of a relative value)
 * turned out to strip the attribute outright rather than just validate it. This is safe to leave
 * unrestricted -- unlike `<a href>`, an `<img src>` is not a script-execution vector in any
 * browser (a `javascript:` URL there simply fails to load as an image).
 */
object StoryHtmlSanitizer {

    private val SAFELIST: Safelist = Safelist.none()
        .addTags("p", "br", "strong", "b", "em", "i", "u", "h2", "h3", "ul", "ol", "li", "a", "img")
        .addAttributes("a", "href")
        .addAttributes("img", "src", "alt")
        .addProtocols("a", "href", "http", "https", "mailto")

    fun sanitize(html: String): String = Jsoup.clean(html, SAFELIST)
}
