package dev.meherwerali.vaultsearch.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ParsedNote(
        String relativePath,
        String title,
        String body,
        Map<String, String> properties,
        List<String> tags,
        List<String> aliases,
        List<String> headings,
        List<String> blockIds,
        List<LinkReference> links,
        String sourceHash,
        Instant modifiedAt
) {
}
