package dev.meherwerali.vaultsearch.search;

import java.util.List;

public record BacklinkResult(String noteId, List<Source> backlinks) {

    public record Source(String noteId, String relativePath, String title) {
    }
}
