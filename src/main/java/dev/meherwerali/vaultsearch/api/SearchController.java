package dev.meherwerali.vaultsearch.api;

import dev.meherwerali.vaultsearch.search.BacklinkResult;
import dev.meherwerali.vaultsearch.search.SearchResult;
import dev.meherwerali.vaultsearch.search.VaultSearchService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
public class SearchController {

    private final VaultSearchService searchService;

    public SearchController(VaultSearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/search")
    SearchResult search(
            @RequestParam("q") @Size(min = 1, max = 256) String query,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        return searchService.search(query, limit);
    }

    @GetMapping("/notes/{noteId}/backlinks")
    BacklinkResult backlinks(
            @PathVariable @Size(min = 64, max = 64) String noteId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(50) int limit) {
        return searchService.backlinks(noteId, limit);
    }
}
