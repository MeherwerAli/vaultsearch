package dev.meherwerali.vaultsearch.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.meherwerali.vaultsearch.config.VaultSearchProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemVaultSourceTest {

    @TempDir
    Path root;

    @Test
    void indexesOnlyVisibleMarkdownWithinTheSizeBoundary() throws Exception {
        Files.writeString(root.resolve("visible.md"), "# Visible");
        Files.writeString(root.resolve("UPPER.MD"), "# Upper");
        Files.writeString(root.resolve("ignored.txt"), "ignored");
        Files.createDirectories(root.resolve(".obsidian"));
        Files.writeString(root.resolve(".obsidian/private.md"), "private");
        Files.writeString(root.resolve("large.md"), "x".repeat(1_025));

        assertThat(source(root, 1_024).scan()).extracting(note -> note.relativePath())
                .containsExactly("UPPER.MD", "visible.md");
    }

    @Test
    void skipsSymlinksEvenWhenTheyPointAtMarkdown() throws Exception {
        Path outside = Files.createTempFile("vaultsearch-outside-", ".md");
        Files.writeString(outside, "# Outside");
        try {
            Files.createSymbolicLink(root.resolve("escape.md"), outside);
            assertThat(source(root, 2_048).scan()).isEmpty();
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void rejectsASymbolicVaultRoot() throws Exception {
        Path realRoot = Files.createDirectory(root.resolve("real"));
        Path linkedRoot = root.resolve("linked");
        Files.createSymbolicLink(linkedRoot, realRoot);

        assertThatThrownBy(() -> source(linkedRoot, 2_048).scan())
                .isInstanceOf(IOException.class)
                .hasMessage("Vault root must be an existing non-symbolic directory");
    }

    private FileSystemVaultSource source(Path vaultRoot, long maxBytes) {
        VaultSearchProperties properties = new VaultSearchProperties();
        properties.setVaultRoot(vaultRoot.toString());
        properties.setMaxFileBytes(maxBytes);
        return new FileSystemVaultSource(properties, new SimpleMeterRegistry());
    }
}
