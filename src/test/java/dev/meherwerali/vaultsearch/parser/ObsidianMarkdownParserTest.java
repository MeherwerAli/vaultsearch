package dev.meherwerali.vaultsearch.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.meherwerali.vaultsearch.domain.ParsedNote;
import dev.meherwerali.vaultsearch.domain.SourceNote;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ObsidianMarkdownParserTest {

    private final ObsidianMarkdownParser parser = new ObsidianMarkdownParser();

    @TempDir
    Path temporaryDirectory;

    @Test
    void parsesFrontmatterTagsAliasesAndInternalLinksWhileIgnoringCode() throws Exception {
        Path note = temporaryDirectory.resolve("Architecture Map.md");
        Files.writeString(note, """
                ---
                title: Platform Architecture
                tags: [Architecture, event-driven]
                aliases:
                  - System Map
                reviewed: 2026-08-15
                ---
                # Ignored fallback title

                Search #reliability and [[Runbooks/Service Health#Checks|health checks]].
                Also see [Gateway](../Decisions/Gateway.md#decision).

                ## Failure semantics

                Durable projection boundary. ^projection-boundary

                ```md
                #not-a-tag [[Not a real link]]
                ```
                Inline `#not-either [[Nope]]` is ignored.
                """);

        ParsedNote parsed = parser.parse(new SourceNote(note, "Maps/Architecture Map.md", Instant.EPOCH));

        assertThat(parsed.title()).isEqualTo("Platform Architecture");
        assertThat(parsed.tags()).containsExactly("architecture", "event-driven", "reliability");
        assertThat(parsed.aliases()).containsExactly("System Map");
        assertThat(parsed.headings()).containsExactly("Ignored fallback title", "Failure semantics");
        assertThat(parsed.blockIds()).containsExactly("projection-boundary");
        assertThat(parsed.links()).extracting(link -> link.target())
                .containsExactly("Runbooks/Service Health", "../Decisions/Gateway");
        assertThat(parsed.properties()).containsEntry("reviewed", "2026-08-15");
        assertThat(parsed.sourceHash()).hasSize(64);
    }

    @Test
    void usesUnicodeHeadingAndInlineTagWithoutFrontmatter() throws Exception {
        Path note = temporaryDirectory.resolve("دليل.md");
        Files.writeString(note, "# دليل التشغيل\n\nمحتوى #تشغيل/سحابة");

        ParsedNote parsed = parser.parse(new SourceNote(note, "دليل.md", Instant.EPOCH));

        assertThat(parsed.title()).isEqualTo("دليل التشغيل");
        assertThat(parsed.tags()).containsExactly("تشغيل/سحابة");
        assertThat(parsed.headings()).containsExactly("دليل التشغيل");
    }

    @Test
    void rejectsMalformedFrontmatterWithoutLeakingItsContents() throws Exception {
        Path note = temporaryDirectory.resolve("broken.md");
        Files.writeString(note, "---\ntags: [broken\n---\n# Broken");

        assertThatThrownBy(() -> parser.parse(new SourceNote(note, "broken.md", Instant.EPOCH)))
                .isInstanceOf(NoteParseException.class)
                .hasMessage("Malformed frontmatter");
    }

    @Test
    void rejectsInvalidUtf8WithoutExposingFileBytes() throws Exception {
        Path note = temporaryDirectory.resolve("invalid.md");
        Files.write(note, new byte[]{(byte) 0xc3, (byte) 0x28});

        assertThatThrownBy(() -> parser.parse(new SourceNote(note, "invalid.md", Instant.EPOCH)))
                .isInstanceOf(NoteParseException.class)
                .hasMessage("Note is not valid UTF-8");
    }
}
