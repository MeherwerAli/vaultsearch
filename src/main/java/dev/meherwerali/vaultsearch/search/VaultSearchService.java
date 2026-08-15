package dev.meherwerali.vaultsearch.search;

import dev.meherwerali.vaultsearch.persistence.IndexTaskRepository;
import dev.meherwerali.vaultsearch.persistence.NoteMetadataRepository;
import dev.meherwerali.vaultsearch.persistence.TaskStatus;
import dev.meherwerali.vaultsearch.sync.IndexRuntimeState;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Service;

@Service
public class VaultSearchService {

    private final ElasticsearchOperations operations;
    private final NoteMetadataRepository notes;
    private final IndexTaskRepository tasks;
    private final IndexRuntimeState runtimeState;
    private final Timer searchTimer;

    public VaultSearchService(ElasticsearchOperations operations, NoteMetadataRepository notes,
                              IndexTaskRepository tasks, IndexRuntimeState runtimeState, MeterRegistry meterRegistry) {
        this.operations = operations;
        this.notes = notes;
        this.tasks = tasks;
        this.runtimeState = runtimeState;
        this.searchTimer = meterRegistry.timer("vaultsearch.search.duration");
    }

    public SearchResult search(String text, int limit) {
        return searchTimer.record(() -> {
            NativeQuery query = NativeQuery.builder()
                    .withQuery(root -> root.multiMatch(match -> match.query(text).fields("title^3", "tags^2", "body")))
                    .withPageable(PageRequest.of(0, limit))
                    .build();
            List<SearchResult.Hit> hits = operations.search(query, NoteSearchDocument.class).stream()
                    .map(hit -> {
                        NoteSearchDocument content = hit.getContent();
                        return new SearchResult.Hit(content.getNoteId(), content.getRelativePath(), content.getTitle(),
                                excerpt(content.getBody(), text), content.getTags(), hit.getScore(), content.getModifiedAt());
                    })
                    .toList();
            return new SearchResult(text, currentState(), runtimeState.lastSuccessfulIndexAt(), hits);
        });
    }

    public BacklinkResult backlinks(String noteId, int limit) {
        if (notes.findByIdAndDeletedFalse(noteId).isEmpty()) {
            throw new NoteNotFoundException(noteId);
        }
        NativeQuery query = NativeQuery.builder()
                .withQuery(root -> root.term(term -> term.field("outgoingNoteIds").value(noteId)))
                .withPageable(PageRequest.of(0, limit))
                .build();
        List<BacklinkResult.Source> sources = operations.search(query, NoteSearchDocument.class).stream()
                .map(hit -> new BacklinkResult.Source(hit.getContent().getNoteId(),
                        hit.getContent().getRelativePath(), hit.getContent().getTitle()))
                .toList();
        return new BacklinkResult(noteId, sources);
    }

    public String currentState() {
        if (runtimeState.lastReconciledAt() == null) {
            return "initializing";
        }
        if (runtimeState.lastFailureCode() != null
                || tasks.countByStatus(TaskStatus.DEAD) > 0
                || tasks.countByStatusIn(Set.of(TaskStatus.PENDING, TaskStatus.PROCESSING)) > 0) {
            return "degraded";
        }
        return "ready";
    }

    private String excerpt(String body, String query) {
        String flattened = body.replaceAll("\\s+", " ").trim();
        String lower = flattened.toLowerCase(Locale.ROOT);
        int match = lower.indexOf(query.toLowerCase(Locale.ROOT));
        int start = match < 0 ? 0 : Math.max(0, match - 80);
        int end = Math.min(flattened.length(), start + 220);
        return (start > 0 ? "…" : "") + flattened.substring(start, end) + (end < flattened.length() ? "…" : "");
    }
}
