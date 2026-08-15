package dev.meherwerali.vaultsearch.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "vaultsearch")
public class VaultSearchProperties {

    @NotBlank
    private String vaultRoot;

    @NotBlank
    private String vaultKey = "default";

    @NotBlank
    private String displayName = "Engineering Knowledge Vault";

    @NotBlank
    private String apiKey;

    @Min(1024)
    @Max(10 * 1024 * 1024)
    private long maxFileBytes = 2 * 1024 * 1024;

    private Duration reconcileInterval = Duration.ofSeconds(60);
    private Duration watcherDebounce = Duration.ofMillis(500);
    private boolean watcherEnabled = true;
    private boolean startupReconcileEnabled = true;
    private boolean scheduledIndexWorkerEnabled = true;
    private int maxIndexAttempts = 8;

    public Path rootPath() {
        return Path.of(vaultRoot).toAbsolutePath().normalize();
    }

    public String getVaultRoot() {
        return vaultRoot;
    }

    public void setVaultRoot(String vaultRoot) {
        this.vaultRoot = vaultRoot;
    }

    public String getVaultKey() {
        return vaultKey;
    }

    public void setVaultKey(String vaultKey) {
        this.vaultKey = vaultKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public long getMaxFileBytes() {
        return maxFileBytes;
    }

    public void setMaxFileBytes(long maxFileBytes) {
        this.maxFileBytes = maxFileBytes;
    }

    public Duration getReconcileInterval() {
        return reconcileInterval;
    }

    public void setReconcileInterval(Duration reconcileInterval) {
        this.reconcileInterval = reconcileInterval;
    }

    public Duration getWatcherDebounce() {
        return watcherDebounce;
    }

    public void setWatcherDebounce(Duration watcherDebounce) {
        this.watcherDebounce = watcherDebounce;
    }

    public boolean isWatcherEnabled() {
        return watcherEnabled;
    }

    public void setWatcherEnabled(boolean watcherEnabled) {
        this.watcherEnabled = watcherEnabled;
    }

    public boolean isStartupReconcileEnabled() {
        return startupReconcileEnabled;
    }

    public void setStartupReconcileEnabled(boolean startupReconcileEnabled) {
        this.startupReconcileEnabled = startupReconcileEnabled;
    }

    public boolean isScheduledIndexWorkerEnabled() {
        return scheduledIndexWorkerEnabled;
    }

    public void setScheduledIndexWorkerEnabled(boolean scheduledIndexWorkerEnabled) {
        this.scheduledIndexWorkerEnabled = scheduledIndexWorkerEnabled;
    }

    public int getMaxIndexAttempts() {
        return maxIndexAttempts;
    }

    public void setMaxIndexAttempts(int maxIndexAttempts) {
        this.maxIndexAttempts = maxIndexAttempts;
    }
}
