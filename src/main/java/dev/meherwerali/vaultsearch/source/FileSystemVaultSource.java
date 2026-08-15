package dev.meherwerali.vaultsearch.source;

import dev.meherwerali.vaultsearch.config.VaultSearchProperties;
import dev.meherwerali.vaultsearch.domain.SourceNote;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class FileSystemVaultSource implements VaultSource {

    private final VaultSearchProperties properties;
    private final Counter skippedFiles;

    public FileSystemVaultSource(VaultSearchProperties properties, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.skippedFiles = meterRegistry.counter("vaultsearch.files.skipped");
    }

    @Override
    public List<SourceNote> scan() throws IOException {
        Path root = verifiedRoot();
        try (Stream<Path> candidates = Files.walk(root)) {
            return candidates
                    .filter(candidate -> isIndexable(root, candidate))
                    .map(candidate -> toSourceNote(root, candidate))
                    .sorted(Comparator.comparing(SourceNote::relativePath))
                    .toList();
        }
    }

    @Override
    public Path root() {
        try {
            return verifiedRoot();
        } catch (IOException exception) {
            throw new IllegalStateException("Vault root is unavailable", exception);
        }
    }

    private Path verifiedRoot() throws IOException {
        Path configured = properties.rootPath();
        if (!Files.isDirectory(configured, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(configured)) {
            throw new IOException("Vault root must be an existing non-symbolic directory");
        }
        return configured.toRealPath(LinkOption.NOFOLLOW_LINKS);
    }

    private boolean isIndexable(Path root, Path candidate) {
        try {
            Path relative = root.relativize(candidate);
            if (relative.getNameCount() == 0 || containsHiddenSegment(relative) || Files.isSymbolicLink(candidate)) {
                return false;
            }
            if (!Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)
                    || !candidate.getFileName().toString().toLowerCase().endsWith(".md")) {
                return false;
            }
            if (Files.size(candidate) > properties.getMaxFileBytes()) {
                skippedFiles.increment();
                return false;
            }
            if (!candidate.toRealPath(LinkOption.NOFOLLOW_LINKS).startsWith(root)) {
                skippedFiles.increment();
                return false;
            }
            return true;
        } catch (IOException exception) {
            skippedFiles.increment();
            return false;
        }
    }

    private boolean containsHiddenSegment(Path relative) {
        for (Path segment : relative) {
            if (segment.toString().startsWith(".")) {
                return true;
            }
        }
        return false;
    }

    private SourceNote toSourceNote(Path root, Path file) {
        try {
            FileTime modified = Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS);
            String relative = root.relativize(file).toString().replace(file.getFileSystem().getSeparator(), "/");
            return new SourceNote(file, relative, Instant.ofEpochMilli(modified.toMillis()));
        } catch (IOException exception) {
            throw new IllegalStateException("Indexed file became unavailable", exception);
        }
    }
}
