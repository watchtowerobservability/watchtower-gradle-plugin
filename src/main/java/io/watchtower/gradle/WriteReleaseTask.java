package io.watchtower.gradle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

/** Writes META-INF/watchtower.properties; the Java SDK reads it as the default release. */
public abstract class WriteReleaseTask extends DefaultTask {
  @Input @Optional public abstract Property<String> getRelease();
  @OutputDirectory public abstract DirectoryProperty getOutputDir();

  @TaskAction
  public void write() throws IOException {
    if (!getRelease().isPresent()) return; // no release configured: the SDK falls back to WT_RELEASE
    Path f = getOutputDir().get().getAsFile().toPath().resolve("META-INF/watchtower.properties");
    Files.createDirectories(f.getParent());
    String r = getRelease().get().replace("\\", "\\\\").replace("\n", " ");
    Files.writeString(f, "release=" + r + "\n", StandardCharsets.UTF_8);
  }
}
