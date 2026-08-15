package dev.meherwerali.vaultsearch.parser;

import dev.meherwerali.vaultsearch.domain.ParsedNote;
import dev.meherwerali.vaultsearch.domain.SourceNote;
import java.io.IOException;

public interface NoteParser {

    ParsedNote parse(SourceNote sourceNote) throws IOException;
}
