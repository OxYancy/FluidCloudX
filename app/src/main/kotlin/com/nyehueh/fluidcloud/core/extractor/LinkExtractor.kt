package com.nyehueh.fluidcloud.core.extractor

import com.nyehueh.fluidcloud.core.model.ExtractedLink
import com.nyehueh.fluidcloud.core.model.FluidConfig

class LinkExtractor(private val config: FluidConfig = FluidConfig()) {

    private val extractionCodeRegex = Regex("Extractioncode:([A-Za-z0-9]+)")
    private val paragraphSplitRegex = Regex("[\\n,，；;\\s]+")
    private val bracketRegex = Regex("\\[[^\\]]*\\]")

    fun extract(rawInput: String): List<ExtractedLink> {
        if (rawInput.isBlank() || rawInput.length > 5000) return emptyList()

        // 1. Normalize Extraction Code Keywords
        var normalized = rawInput
        config.extractionKeywords.forEach { keyword ->
            normalized = normalized.replace(keyword, " Extractioncode:")
        }
        normalized = normalized.replace(bracketRegex, "").trim()

        // 2. Allow only allowed characters + newlines
        val allowedCharSet = (config.allowChar + " \n").toSet()
        val filteredText = buildString {
            for (ch in normalized) {
                if (ch in allowedCharSet) append(ch)
            }
        }.trim()

        // 3. Separate HTTP/HTTPS prefix boundary
        val preprocessed = filteredText
            .replace("http://", " http://")
            .replace("https://", " https://")

        val tokens = preprocessed.split(paragraphSplitRegex).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()

        // 4. Find all Extraction Codes in paragraph
        val extractionCodes = mutableListOf<String>()
        tokens.forEach { token ->
            val match = extractionCodeRegex.find(token)
            if (match != null) {
                val code = match.groupValues.getOrNull(1)
                if (!code.isNullOrBlank()) {
                    extractionCodes.add(code)
                }
            }
        }

        // 5. Scan tokens for candidate links
        val rawLinks = mutableListOf<String>()
        val candidateTokens = mutableListOf<String>()

        config.topLevelDomains.forEach { tld ->
            val tldSlash = ".$tld/"
            val tldDot = ".$tld"

            tokens.forEach { token ->
                if (token.contains(tldSlash) && token.length > tld.length + 2) {
                    if (token !in rawLinks) rawLinks.add(token)
                } else if (token !in candidateTokens) {
                    candidateTokens.add(token)
                }
            }
        }

        config.topLevelDomains.forEach { tld ->
            val suffix = ".$tld"
            candidateTokens.forEach { token ->
                if (token.endsWith(suffix) && token.length > tld.length + 1) {
                    val isEmail = config.emailKeywords.any { emailKwd ->
                        token.contains("@$emailKwd")
                    }
                    if (!isEmail && token !in rawLinks) {
                        rawLinks.add(token)
                    }
                } else if ((token.startsWith("http://") || token.startsWith("https://")) &&
                    token.length > 8 && token.contains(".")
                ) {
                    if (token !in rawLinks) {
                        rawLinks.add(token)
                    }
                }
            }
        }

        // 6. Complete Scheme / Protocol headers (http:// / https://)
        val fullLinks = rawLinks.map { link ->
            var finalUrl = link.replace("http://", "").replace("https://", "").split(" ").first()
            val useHttps = config.httpsOnlyKeywords.any { finalUrl.contains(it) }
            if (useHttps) "https://$finalUrl" else "http://$finalUrl"
        }.distinct()

        // 7. Blacklist filtering
        val cleanedLinks = fullLinks.filterNot { link ->
            config.linkBlacklistKeywords.any { kwd -> kwd != "无" && link.contains(kwd) }
        }

        // 8. Associate extraction codes with links
        val codeForLinks = extractionCodes.firstOrNull()

        return cleanedLinks.map { url ->
            ExtractedLink(
                url = url,
                code = codeForLinks,
                rawInput = rawInput
            )
        }
    }
}
