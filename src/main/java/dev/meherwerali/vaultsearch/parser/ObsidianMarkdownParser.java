package dev.meherwerali.vaultsearch.parser;

import dev.meherwerali.vaultsearch.domain.LinkReference;
import dev.meherwerali.vaultsearch.domain.ParsedNote;
import dev.meherwerali.vaultsearch.domain.SourceNote;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

@Component
public class ObsidianMarkdownParser implements NoteParser {

    private static final Pattern HEADING = Pattern.compile("(?m)^#{1,6}[ \\t]+(.+?)[ \\t]*#*[ \\t]*$");
    private static final Pattern BLOCK_ID = Pattern.compile("(?m)(?:^|\\s)\\^([A-Za-z0-9-]+)[ \\t]*$");
    private static final Pattern INLINE_CODE = Pattern.compile("`[^`\\r\\n]*`");
    private static final Pattern INLINE_TAG = Pattern.compile("(?<![\\p{L}\\p{N}_])#([\\p{L}\\p{N}_/-]*[\\p{L}_-][\\p{L}\\p{N}_/-]*)");
    private static final Pattern WIKILINK = Pattern.compile("!?\\[\\[([^]\\r\\n]+)]]");
    private static final Pattern MARKDOWN_LINK = Pattern.compile("(?<!!)\\[[^]\\r\\n]+]\\(([^)\\r\\n]+)\\)");
    private static final Pattern SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:");

    private final Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));

    @Override
    public ParsedNote parse(SourceNote sourceNote) throws IOException {
        byte[] bytes = Files.readAllBytes(sourceNote.absolutePath());
        String content = decodeUtf8(bytes);
        Frontmatter frontmatter = extractFrontmatter(content);
        String searchableBody = frontmatter.body();
        String metadataBody = withoutCode(searchableBody);

        Map<String, String> properties = stringifyProperties(frontmatter.values());
        List<String> headings = matches(metadataBody, HEADING);
        List<String> blockIds = matches(metadataBody, BLOCK_ID);

        String title = scalar(frontmatter.values().get("title"));
        if (title == null || title.isBlank()) {
            title = headings.isEmpty() ? filenameStem(sourceNote.relativePath()) : headings.get(0);
        }

        Set<String> tags = new LinkedHashSet<>();
        addValues(tags, frontmatter.values().get("tags"), true);
        Matcher inlineTags = INLINE_TAG.matcher(metadataBody);
        while (inlineTags.find()) {
            tags.add(normalizeTag(inlineTags.group(1)));
        }

        Set<String> aliases = new LinkedHashSet<>();
        addValues(aliases, frontmatter.values().get("aliases"), false);

        List<LinkReference> links = new ArrayList<>();
        Matcher wikiLinks = WIKILINK.matcher(metadataBody);
        while (wikiLinks.find()) {
            String[] aliasParts = wikiLinks.group(1).split("\\|", 2);
            String target = normalizeLinkTarget(aliasParts[0]);
            if (!target.isBlank()) {
                links.add(new LinkReference(target, aliasParts.length == 2 ? aliasParts[1].trim() : null));
            }
        }

        Matcher markdownLinks = MARKDOWN_LINK.matcher(metadataBody);
        while (markdownLinks.find()) {
            String target = normalizeMarkdownTarget(markdownLinks.group(1));
            if (target != null && !target.isBlank()) {
                links.add(new LinkReference(target, null));
            }
        }

        return new ParsedNote(
                sourceNote.relativePath(),
                title,
                searchableBody,
                Map.copyOf(properties),
                List.copyOf(tags),
                List.copyOf(aliases),
                List.copyOf(headings),
                List.copyOf(blockIds),
                List.copyOf(links),
                sha256(bytes),
                sourceNote.modifiedAt()
        );
    }

    private String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new NoteParseException("Note is not valid UTF-8", exception);
        }
    }

    private Frontmatter extractFrontmatter(String content) {
        String normalized = content.replace("\r\n", "\n");
        if (!normalized.startsWith("---\n")) {
            return new Frontmatter(Map.of(), normalized);
        }
        int closing = normalized.indexOf("\n---\n", 4);
        if (closing < 0) {
            throw new NoteParseException("Malformed frontmatter", null);
        }
        String yamlBlock = normalized.substring(4, closing);
        try {
            Object loaded = yaml.load(yamlBlock);
            if (loaded == null) {
                return new Frontmatter(Map.of(), normalized.substring(closing + 5));
            }
            if (!(loaded instanceof Map<?, ?> rawMap)) {
                throw new NoteParseException("Frontmatter must be an object", null);
            }
            Map<String, Object> values = new LinkedHashMap<>();
            rawMap.forEach((key, value) -> values.put(String.valueOf(key), value));
            return new Frontmatter(values, normalized.substring(closing + 5));
        } catch (NoteParseException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new NoteParseException("Malformed frontmatter", exception);
        }
    }

    private String withoutCode(String body) {
        StringBuilder result = new StringBuilder(body.length());
        boolean fenced = false;
        String fenceMarker = null;
        for (String line : body.split("\\n", -1)) {
            String trimmed = line.stripLeading();
            if (!fenced && (trimmed.startsWith("```") || trimmed.startsWith("~~~"))) {
                fenced = true;
                fenceMarker = trimmed.substring(0, 3);
                result.append('\n');
                continue;
            }
            if (fenced) {
                if (trimmed.startsWith(fenceMarker)) {
                    fenced = false;
                }
                result.append('\n');
                continue;
            }
            result.append(INLINE_CODE.matcher(line).replaceAll(" ")).append('\n');
        }
        return result.toString();
    }

    private Map<String, String> stringifyProperties(Map<String, Object> values) {
        Map<String, String> properties = new LinkedHashMap<>();
        values.forEach((key, value) -> properties.put(key, stringify(value)));
        return properties;
    }

    private List<String> matches(String body, Pattern pattern) {
        List<String> values = new ArrayList<>();
        Matcher matcher = pattern.matcher(body);
        while (matcher.find()) {
            values.add(matcher.group(1).trim());
        }
        return values;
    }

    private String stringify(Object value) {
        if (value instanceof java.util.Date date) {
            return DateTimeFormatter.ISO_LOCAL_DATE.format(date.toInstant().atZone(ZoneOffset.UTC).toLocalDate());
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).reduce((left, right) -> left + "," + right).orElse("");
        }
        return String.valueOf(value);
    }

    private void addValues(Set<String> destination, Object value, boolean tags) {
        if (value == null) {
            return;
        }
        if (value instanceof Collection<?> collection) {
            collection.stream().map(String::valueOf).map(String::trim).filter(item -> !item.isBlank())
                    .map(item -> tags ? normalizeTag(item) : item).forEach(destination::add);
            return;
        }
        String raw = String.valueOf(value);
        for (String item : raw.split("[,\\s]+")) {
            if (!item.isBlank()) {
                destination.add(tags ? normalizeTag(item) : item.trim());
            }
        }
    }

    private String normalizeTag(String tag) {
        return tag.replaceFirst("^#", "").toLowerCase(Locale.ROOT);
    }

    private String scalar(Object value) {
        return value instanceof CharSequence ? value.toString().trim() : null;
    }

    private String normalizeLinkTarget(String raw) {
        String target = raw.trim();
        int heading = target.indexOf('#');
        if (heading >= 0) {
            target = target.substring(0, heading);
        }
        int block = target.indexOf('^');
        if (block >= 0) {
            target = target.substring(0, block);
        }
        return target.replace("\\", "/").replaceFirst("(?i)\\.md$", "").trim();
    }

    private String normalizeMarkdownTarget(String raw) {
        String target = raw.trim().replace("%20", " ");
        if (target.startsWith("#") || target.startsWith("//") || SCHEME.matcher(target).find()) {
            return null;
        }
        return normalizeLinkTarget(target);
    }

    private String filenameStem(String path) {
        String filename = path.substring(path.lastIndexOf('/') + 1);
        return filename.replaceFirst("(?i)\\.md$", "");
    }

    private String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private record Frontmatter(Map<String, Object> values, String body) {
    }
}
