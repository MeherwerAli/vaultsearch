package dev.meherwerali.vaultsearch.search;

import java.time.Instant;
import java.util.List;

public record SearchResult(String query, String indexState, Instant lastSuccessfulIndexAt, List<Hit> hits) {

    public record Hit(String noteId, String relativePath, String title, String excerpt,
                      List<String> tags, double score, Instant modifiedAt) {
    }
}
