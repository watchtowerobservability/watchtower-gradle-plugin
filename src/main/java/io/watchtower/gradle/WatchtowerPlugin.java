package io.watchtower.gradle;

import java.io.File;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.TaskProvider;

public class WatchtowerPlugin implements Plugin<Project> {
  @Override
  public void apply(Project p) {
    WatchtowerExtension ext = p.getExtensions().create("watchtower", WatchtowerExtension.class);
    ext.getUrl().convention(p.getProviders().environmentVariable("WT_API"));
    ext.getProject().convention(p.getProviders().environmentVariable("WT_PROJECT"));
    ext.getAuthToken().convention(p.getProviders().environmentVariable("WT_API_KEY"));
    ext.getRelease().convention(p.provider(() -> {
      String v = String.valueOf(p.getVersion());
      return "unspecified".equals(v) ? null : v;
    }));
    File build = p.getLayout().getBuildDirectory().get().getAsFile();
    ext.getMappingFiles().from(p.fileTree(new File(build, "outputs/mapping"), t -> t.include("**/mapping.txt")));
    ext.getNativeSymbolDirs().from(new File(build, "intermediates/native_debug_metadata"),
        new File(build, "intermediates/merged_native_libs"));

    TaskProvider<WriteReleaseTask> write = p.getTasks().register("watchtowerWriteRelease", WriteReleaseTask.class, t -> {
      t.setGroup("watchtower");
      t.setDescription("Writes the release name where the Watchtower SDK reads it at runtime");
      t.getRelease().set(ext.getRelease());
      t.getOutputDir().set(p.getLayout().getBuildDirectory().dir("generated/watchtower/resources"));
    });
    TaskProvider<UploadTask> upload = p.getTasks().register("watchtowerUpload", UploadTask.class, t -> {
      t.setGroup("watchtower");
      t.setDescription("Uploads ProGuard/R8 mappings and native symbols to Watchtower");
      t.getUrl().set(ext.getUrl());
      t.getProjectId().set(ext.getProject());
      t.getRelease().set(ext.getRelease());
      t.getAuthToken().set(ext.getAuthToken());
      t.getMappingFiles().from(ext.getMappingFiles());
      t.getNativeSymbolDirs().from(ext.getNativeSymbolDirs());
    });

    p.getPlugins().withId("java", x -> {
      p.getExtensions().getByType(JavaPluginExtension.class).getSourceSets().getByName("main").getResources()
          .srcDir(write.map(t -> t.getOutputDir()));
      p.getTasks().named("processResources", t -> t.dependsOn(write));
    });
    p.getPlugins().withId("com.android.application", x -> wireAndroid(p, write, upload));
    p.getPlugins().withId("com.android.library", x -> wireAndroid(p, write, upload));
  }

  /** Android: add the generated dir as a resource dir (via reflection, so AGP is not a dependency) and upload after release builds. */
  private static void wireAndroid(Project p, TaskProvider<WriteReleaseTask> write, TaskProvider<UploadTask> upload) {
    try {
      Object android = p.getExtensions().getByName("android");
      Object sourceSets = android.getClass().getMethod("getSourceSets").invoke(android);
      Object main = sourceSets.getClass().getMethod("getByName", String.class).invoke(sourceSets, "main");
      Object resources = main.getClass().getMethod("getResources").invoke(main);
      resources.getClass().getMethod("srcDir", Object.class).invoke(resources, write.map(t -> t.getOutputDir()));
    } catch (ReflectiveOperationException e) {
      p.getLogger().warn("Watchtower: could not add the release resource to the Android source set ({}); "
          + "set the release in code instead", e.toString());
    }
    p.getTasks().matching(t -> isReleaseBuild(t.getName())).configureEach(t -> {
      t.dependsOn(write);
      t.finalizedBy(upload);
    });
    p.getTasks().matching(t -> t.getName().startsWith("process") && t.getName().endsWith("JavaRes"))
        .configureEach(t -> t.dependsOn(write));
  }

  static boolean isReleaseBuild(String task) {
    return (task.startsWith("assemble") || task.startsWith("bundle")) && task.endsWith("Release");
  }
}
