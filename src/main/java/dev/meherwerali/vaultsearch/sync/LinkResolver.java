package dev.meherwerali.vaultsearch.sync;

import dev.meherwerali.vaultsearch.domain.LinkReference;
import dev.meherwerali.vaultsearch.domain.ParsedNote;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class LinkResolver {

    public List<ResolvedNote> resolve(String vaultKey, List<ParsedNote> notes) {
        Map<String, ParsedNote> byPath = new HashMap<>();
        Map<String, List<ParsedNote>> byStem = new HashMap<>();
        Map<String, List<ParsedNote>> byAlias = new HashMap<>();
        notes.forEach(note -> {
            byPath.put(pathKey(withoutExtension(note.relativePath())), note);
            byStem.computeIfAbsent(pathKey(stem(note.relativePath())), ignored -> new ArrayList<>()).add(note);
            note.aliases().forEach(alias -> byAlias.computeIfAbsent(pathKey(alias), ignored -> new ArrayList<>()).add(note));
        });

        return notes.stream().map(note -> resolveNote(vaultKey, note, byPath, byStem, byAlias)).toList();
    }

    private ResolvedNote resolveNote(String vaultKey, ParsedNote note, Map<String, ParsedNote> byPath,
                                     Map<String, List<ParsedNote>> byStem, Map<String, List<ParsedNote>> byAlias) {
        Set<String> outgoing = new LinkedHashSet<>();
        Set<String> unresolved = new LinkedHashSet<>();
        for (LinkReference link : note.links()) {
            ParsedNote target = resolveTarget(note.relativePath(), link.target(), byPath, byStem, byAlias);
            if (target == null) {
                unresolved.add(link.target());
            } else {
                outgoing.add(noteId(vaultKey, target.relativePath()));
            }
        }
        String noteId = noteId(vaultKey, note.relativePath());
        String projectionHash = Hashing.sha256(note.sourceHash() + "\n" + String.join("\n", outgoing)
                + "\n--unresolved--\n" + String.join("\n", unresolved));
        return new ResolvedNote(noteId, note, List.copyOf(outgoing), List.copyOf(unresolved), projectionHash);
    }

    private ParsedNote resolveTarget(String sourcePath, String rawTarget, Map<String, ParsedNote> byPath,
                                     Map<String, List<ParsedNote>> byStem, Map<String, List<ParsedNote>> byAlias) {
        String target = withoutExtension(rawTarget.replace('\\', '/'));
        if (target.isBlank()) {
            return null;
        }

        String sourceParent = Path.of(sourcePath).getParent() == null ? "" : Path.of(sourcePath).getParent().toString().replace('\\', '/');
        String relativeCandidate = normalizePath(sourceParent.isBlank() ? target : sourceParent + "/" + target);
        ParsedNote exact = byPath.get(pathKey(relativeCandidate));
        if (exact != null) {
            return exact;
        }

        String rootCandidate = normalizePath(target.replaceFirst("^/", ""));
        exact = byPath.get(pathKey(rootCandidate));
        if (exact != null) {
            return exact;
        }

        List<ParsedNote> stems = byStem.getOrDefault(pathKey(stem(target)), List.of());
        if (stems.size() == 1) {
            return stems.get(0);
        }
        List<ParsedNote> aliases = byAlias.getOrDefault(pathKey(target), List.of());
        return aliases.size() == 1 ? aliases.get(0) : null;
    }

    private String normalizePath(String value) {
        final Path normalized;
        try {
            normalized = Path.of(value).normalize();
        } catch (RuntimeException exception) {
            return "__outside_vault__";
        }
        if (normalized.isAbsolute() || normalized.startsWith("..")) {
            return "__outside_vault__";
        }
        return normalized.toString().replace('\\', '/');
    }

    private String withoutExtension(String path) {
        return path.replaceFirst("(?i)\\.md$", "");
    }

    private String stem(String path) {
        String value = withoutExtension(path);
        return value.substring(value.lastIndexOf('/') + 1);
    }

    private String pathKey(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    public String noteId(String vaultKey, String relativePath) {
        return Hashing.sha256(vaultKey + "\0" + relativePath.replace('\\', '/'));
    }
}
