package dev.meherwerali.vaultsearch.source;

import dev.meherwerali.vaultsearch.domain.SourceNote;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface VaultSource {

    List<SourceNote> scan() throws IOException;

    Path root();
}
