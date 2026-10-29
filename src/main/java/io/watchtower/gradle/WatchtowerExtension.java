package io.watchtower.gradle;

import javax.inject.Inject;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

/** {@code watchtower { ... }}. Defaults come from WT_API, WT_PROJECT, WT_API_KEY and the project version. */
public abstract class WatchtowerExtension {
  private final Property<String> url;
  private final Property<String> project;
  private final Property<String> authToken;
  private final Property<String> release;
  private final ConfigurableFileCollection mappingFiles;
  private final ConfigurableFileCollection nativeSymbolDirs;

  @Inject
  public WatchtowerExtension(ObjectFactory o) {
    url = o.property(String.class);
    project = o.property(String.class);
    authToken = o.property(String.class);
    release = o.property(String.class);
    mappingFiles = o.fileCollection();
    nativeSymbolDirs = o.fileCollection();
  }

  /** API base URL, e.g. https://watchtower.example.com. Default $WT_API. */
  public Property<String> getUrl() { return url; }
  /** Project id. Default $WT_PROJECT. */
  public Property<String> getProject() { return project; }
  /** Project-admin API key. Default $WT_API_KEY. Prefer the environment variable over a build file. */
  public Property<String> getAuthToken() { return authToken; }
  /** Release name; must match what the app reports as service.version. Default: project version. */
  public Property<String> getRelease() { return release; }
  /** ProGuard/R8 mapping files. Default: build/outputs/mapping/**&#47;mapping.txt. */
  public ConfigurableFileCollection getMappingFiles() { return mappingFiles; }
  /** Directories scanned for *.so files with debug info. Default: Android native debug metadata and merged native libs. */
  public ConfigurableFileCollection getNativeSymbolDirs() { return nativeSymbolDirs; }
}
