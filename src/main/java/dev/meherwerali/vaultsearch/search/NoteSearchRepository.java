package dev.meherwerali.vaultsearch.search;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface NoteSearchRepository extends ElasticsearchRepository<NoteSearchDocument, String> {
}
