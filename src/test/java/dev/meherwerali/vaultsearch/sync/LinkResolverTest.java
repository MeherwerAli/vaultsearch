package dev.meherwerali.vaultsearch.sync;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meherwerali.vaultsearch.domain.LinkReference;
import dev.meherwerali.vaultsearch.domain.ParsedNote;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LinkResolverTest {

    private final LinkResolver resolver = new LinkResolver();

    @Test
    void resolvesRelativeRootStemAndAliasTargetsAndKeepsAmbiguityExplicit() {
        ParsedNote source = note("Maps/Architecture.md", "Architecture", List.of(
                new LinkReference("../Runbooks/Health", null),
                new LinkReference("Decisions/Gateway", null),
                new LinkReference("Circuit Breaker", null),
                new LinkReference("Shared", null),
                new LinkReference("../../outside", null)
        ), List.of());
        ParsedNote health = note("Runbooks/Health.md", "Health", List.of(), List.of());
        ParsedNote gateway = note("Decisions/Gateway.md", "Gateway", List.of(), List.of("Circuit Breaker"));
        ParsedNote sharedOne = note("A/Shared.md", "Shared A", List.of(), List.of());
        ParsedNote sharedTwo = note("B/Shared.md", "Shared B", List.of(), List.of());

        ResolvedNote resolved = resolver.resolve("default", List.of(source, health, gateway, sharedOne, sharedTwo)).get(0);

        assertThat(resolved.outgoingNoteIds()).containsExactly(
                resolver.noteId("default", "Runbooks/Health.md"),
                resolver.noteId("default", "Decisions/Gateway.md")
        );
        assertThat(resolved.unresolvedLinks()).containsExactly("Shared", "../../outside");
    }

    @Test
    void noteIdIsStableForVaultAndNormalizedRelativePath() {
        assertThat(resolver.noteId("default", "Maps/Architecture.md"))
                .isEqualTo(resolver.noteId("default", "Maps/Architecture.md"))
                .hasSize(64)
                .isNotEqualTo(resolver.noteId("other", "Maps/Architecture.md"));
    }

    private ParsedNote note(String path, String title, List<LinkReference> links, List<String> aliases) {
        return new ParsedNote(path, title, "body", Map.of(), List.of(), aliases, List.of(), List.of(), links,
                Hashing.sha256(path), Instant.EPOCH);
    }
}
