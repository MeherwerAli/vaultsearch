package dev.meherwerali.vaultsearch.search;

import dev.meherwerali.vaultsearch.sync.ResolvedNote;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "vault-notes", createIndex = true)
public class NoteSearchDocument {

    @Id
    private String noteId;

    @Field(type = FieldType.Keyword)
    private String vaultId;

    @Field(type = FieldType.Keyword)
    private String relativePath;

    @Field(type = FieldType.Text)
    private String title;

    @Field(type = FieldType.Text)
    private String body;

    @Field(type = FieldType.Keyword)
    private List<String> tags;

    @Field(type = FieldType.Keyword)
    private List<String> aliases;

    @Field(type = FieldType.Text)
    private List<String> headings;

    @Field(type = FieldType.Keyword)
    private List<String> blockIds;

    @Field(type = FieldType.Flattened)
    private Map<String, String> properties;

    @Field(type = FieldType.Keyword)
    private List<String> outgoingNoteIds;

    @Field(type = FieldType.Keyword)
    private List<String> unresolvedLinks;

    @Field(type = FieldType.Keyword)
    private String sourceHash;

    @Field(type = FieldType.Date)
    private Instant modifiedAt;

    public NoteSearchDocument() {
    }

    public static NoteSearchDocument from(String vaultId, ResolvedNote resolved) {
        NoteSearchDocument document = new NoteSearchDocument();
        document.noteId = resolved.noteId();
        document.vaultId = vaultId;
        document.relativePath = resolved.note().relativePath();
        document.title = resolved.note().title();
        document.body = resolved.note().body();
        document.tags = resolved.note().tags();
        document.aliases = resolved.note().aliases();
        document.headings = resolved.note().headings();
        document.blockIds = resolved.note().blockIds();
        document.properties = resolved.note().properties();
        document.outgoingNoteIds = resolved.outgoingNoteIds();
        document.unresolvedLinks = resolved.unresolvedLinks();
        document.sourceHash = resolved.note().sourceHash();
        document.modifiedAt = resolved.note().modifiedAt();
        return document;
    }

    public String getNoteId() {
        return noteId;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public List<String> getTags() {
        return tags;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public List<String> getHeadings() {
        return headings;
    }

    public List<String> getBlockIds() {
        return blockIds;
    }

    public List<String> getOutgoingNoteIds() {
        return outgoingNoteIds;
    }

    public List<String> getUnresolvedLinks() {
        return unresolvedLinks;
    }

    public Instant getModifiedAt() {
        return modifiedAt;
    }
}
