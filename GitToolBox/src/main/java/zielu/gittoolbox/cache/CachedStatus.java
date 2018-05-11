package zielu.gittoolbox.cache;

import com.codahale.metrics.Timer;
import com.google.common.base.Objects;
import com.intellij.openapi.diagnostic.Logger;
import git4idea.repo.GitRepository;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import zielu.gittoolbox.GitToolBoxAppMetrics;
import zielu.gittoolbox.status.GitAheadBehindCount;
import zielu.gittoolbox.status.GitStatusCalculator;
import zielu.gittoolbox.util.GtUtil;
import zielu.gittoolbox.util.diagnostics.PerfWatch;

class CachedStatus {
  private static final Logger LOG = Logger.getInstance(CachedStatus.class);
  private final PerfWatch repoStatusCreateWatch = PerfWatch.create("Repo status create");
  private final AtomicBoolean invalid = new AtomicBoolean(true);
  private final AtomicBoolean newStatus = new AtomicBoolean(true);
  private final AtomicReference<RepoInfo> repoInfo = new AtomicReference<>(RepoInfo.empty());
  private final String repoName;
  @Nullable
  private GitAheadBehindCount count;
  @Nullable
  private RepoStatus status;

  private CachedStatus(String repoName) {
    this.repoName = repoName;
  }

  public static CachedStatus create(GitRepository repository) {
    return new CachedStatus(GtUtil.name(repository));
  }

  public synchronized void update(@NotNull GitRepository repo, @NotNull GitStatusCalculator calculator,
                                  @NotNull Consumer<RepoInfo> infoConsumer) {
    newStatus.set(false);
    if (invalid.get()) {
      RepoStatus currentStatus = createStatus(repo);
      LOG.debug("State update [", repoName, "]:\nnew=", currentStatus, "\nold=", status);
      if (!Objects.equal(status, currentStatus)) {
        updateStatus(repo, calculator, infoConsumer, currentStatus);
      } else {
        LOG.debug("Status did not change [", repoName, "]: ", count);
      }
    } else {
      LOG.debug("Status is still valid [", repoName, "]: ", count);
    }
  }

  private void updateStatus(@NotNull GitRepository repo, @NotNull GitStatusCalculator calculator,
                            @NotNull Consumer<RepoInfo> infoConsumer, RepoStatus currentStatus) {
    GitAheadBehindCount oldCount = count;
    Timer statusUpdateLatency = GitToolBoxAppMetrics.getInstance().timer("status_update");
    count = statusUpdateLatency
        .timeSupplier(() -> calculator.aheadBehindStatus(repo, currentStatus.localHash(), currentStatus.remoteHash()));
    LOG.debug("Updated stale status [", repoName, "]: ", oldCount, " > ", count);
    if (!currentStatus.sameHashes(count)) {
      LOG.warn("Hash mismatch between count and status: " + count + " <> " + currentStatus);
    }
    status = currentStatus;
    RepoInfo newInfo = RepoInfo.create(status, count);
    repoInfo.set(newInfo);
    invalid.set(false);
    infoConsumer.accept(newInfo);
  }

  private RepoStatus createStatus(GitRepository repository) {
    repoStatusCreateWatch.start();
    RepoStatus currentStatus = RepoStatus.create(repository);
    repoStatusCreateWatch.finish();
    return currentStatus;
  }

  public void invalidate() {
    invalid.set(true);
  }

  public boolean isInvalid() {
    return invalid.get();
  }

  public boolean isNew() {
    return newStatus.get();
  }

  @NotNull
  public RepoInfo get() {
    return repoInfo.get();
  }
}
