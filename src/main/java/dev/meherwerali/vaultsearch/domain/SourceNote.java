package dev.meherwerali.vaultsearch.domain;

import java.nio.file.Path;
import java.time.Instant;

public record SourceNote(Path absolutePath, String relativePath, Instant modifiedAt) {
}
