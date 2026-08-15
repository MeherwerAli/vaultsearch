package dev.meherwerali.vaultsearch.search;

public class NoteNotFoundException extends RuntimeException {

    public NoteNotFoundException(String noteId) {
        super("Note not found: " + noteId);
    }
}
