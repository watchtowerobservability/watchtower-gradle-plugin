package io.watchtower.gradle;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

/** Uploads mapping files (kind proguard) and native symbol files (kind elf) to the release. */
public abstract class UploadTask extends DefaultTask {
  @Input @Optional public abstract Property<String> getUrl();
  @Input @Optional public abstract Property<String> getProjectId();
  @Input @Optional public abstract Property<String> getRelease();
  /** Not an input: the key must never reach task history or build scans. */
  @Internal public abstract Property<String> getAuthToken();
  @InputFiles public abstract ConfigurableFileCollection getMappingFiles();
  @InputFiles public abstract ConfigurableFileCollection getNativeSymbolDirs();

  @TaskAction
  public void upload() throws IOException, InterruptedException {
    if (!getRelease().isPresent() || getRelease().get().isEmpty() || "unspecified".equals(getRelease().get())) {
      throw new GradleException("watchtower.release is not set (and the project version is unspecified)");
    }
    List<Object[]> work = new ArrayList<>(); // {kind, name, file}
    for (File f : getMappingFiles().getFiles()) {
      if (f.isFile()) work.add(new Object[] {"proguard", mappingName(f), f});
    }
    for (File dir : getNativeSymbolDirs().getFiles()) {
      if (!dir.isDirectory()) continue;
      try (var s = Files.walk(dir.toPath())) {
        s.filter(p -> p.toString().endsWith(".so") && Files.isRegularFile(p))
            .forEach(p -> work.add(new Object[] {"elf", dir.toPath().relativize(p).toString().replace(File.separatorChar, '/'), p.toFile()}));
      }
    }
    if (work.isEmpty()) {
      getLogger().lifecycle("Watchtower: no mapping files or native symbols found, nothing to upload");
      return;
    }
    WatchtowerClient c = new WatchtowerClient(getUrl().getOrNull(), getProjectId().getOrNull(), getAuthToken().getOrNull());
    String release = getRelease().get();
    c.ensureRelease(release);
    for (Object[] w : work) {
      File f = (File) w[2];
      byte[] data = Files.readAllBytes(f.toPath());
      c.upload((String) w[0], (String) w[1], release, "proguard".equals(w[0]) ? WatchtowerClient.debugId(data) : null, data);
      getLogger().lifecycle("Watchtower: uploaded {} ({})", w[1], w[0]);
    }
  }

  /** Android keeps maps at outputs/mapping/&lt;variant&gt;/mapping.txt; keep the variant in the name so variants do not collide. */
  static String mappingName(File f) {
    File parent = f.getParentFile();
    return parent == null ? f.getName() : parent.getName() + "/" + f.getName();
  }
}
