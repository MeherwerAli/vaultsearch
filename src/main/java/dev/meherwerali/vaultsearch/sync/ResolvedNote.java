package dev.meherwerali.vaultsearch.sync;

import dev.meherwerali.vaultsearch.domain.ParsedNote;
import java.util.List;

public record ResolvedNote(
        String noteId,
        ParsedNote note,
        List<String> outgoingNoteIds,
        List<String> unresolvedLinks,
        String projectionHash
) {
}
