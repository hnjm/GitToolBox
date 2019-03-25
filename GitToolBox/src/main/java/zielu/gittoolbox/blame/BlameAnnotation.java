package zielu.gittoolbox.blame;

import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import zielu.gittoolbox.revision.RevisionInfo;

public interface BlameAnnotation {
  @NotNull
  RevisionInfo getBlame(int lineNumber);

  boolean isChanged(@NotNull VcsRevisionNumber revision);

  boolean updateRevision(@NotNull RevisionInfo revisionInfo);

  @Nullable
  VirtualFile getVirtualFile();
}
