package io.watchtower.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginTest {
  /** Records "METHOD path?query auth encoding body" per request. */
  private static HttpServer server(List<String> seen) throws Exception {
    HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    s.createContext("/", ex -> {
      byte[] raw = ex.getRequestBody().readAllBytes();
      String enc = ex.getRequestHeaders().getFirst("Content-Encoding");
      byte[] body = "gzip".equals(enc) ? new GZIPInputStream(new java.io.ByteArrayInputStream(raw)).readAllBytes() : raw;
      seen.add(ex.getRequestMethod() + " " + ex.getRequestURI() + " " + ex.getRequestHeaders().getFirst("Authorization")
          + " " + enc + " " + new String(body));
      ex.sendResponseHeaders(201, -1);
      ex.close();
    });
    s.start();
    return s;
  }

  @Test
  void uploadsReleaseThenMappingWithDerivedDebugId(@TempDir File dir) throws Exception {
    List<String> seen = new ArrayList<>();
    HttpServer s = server(seen);
    try {
      Project p = ProjectBuilder.builder().withProjectDir(dir).build();
      p.getPlugins().apply("io.github.watchtowerobservability.watchtower");
      File map = new File(dir, "build/outputs/mapping/release/mapping.txt");
      map.getParentFile().mkdirs();
      Files.writeString(map.toPath(), "a.b.C -> a:\n");
      WatchtowerExtension ext = p.getExtensions().getByType(WatchtowerExtension.class);
      ext.getUrl().set("http://127.0.0.1:" + s.getAddress().getPort());
      ext.getProject().set("prj_1");
      ext.getAuthToken().set("test-key-not-real");
      ext.getRelease().set("1.2.3");
      ((UploadTask) p.getTasks().getByName("watchtowerUpload")).upload();
    } finally {
      s.stop(0);
    }
    assertEquals(2, seen.size());
    assertTrue(seen.get(0).startsWith("POST /v1/projects/prj_1/releases Bearer test-key-not-real null {\"version\":\"1.2.3\"}"));
    String up = seen.get(1);
    assertTrue(up.contains("/artifacts?kind=proguard&name=release%2Fmapping.txt&release=1.2.3&debug_id="), up);
    assertTrue(up.contains(" gzip a.b.C -> a:"), up);
  }

  @Test
  void missingCredentialsFailWithoutLeakingAnything(@TempDir File dir) throws Exception {
    Project p = ProjectBuilder.builder().withProjectDir(dir).build();
    p.getPlugins().apply("io.github.watchtowerobservability.watchtower");
    File map = new File(dir, "build/outputs/mapping/release/mapping.txt");
    map.getParentFile().mkdirs();
    Files.writeString(map.toPath(), "x");
    WatchtowerExtension ext = p.getExtensions().getByType(WatchtowerExtension.class);
    ext.getRelease().set("1");
    ext.getAuthToken().set("");
    assertThrows(IllegalArgumentException.class, () -> ((UploadTask) p.getTasks().getByName("watchtowerUpload")).upload());
  }

  @Test
  void writesReleaseResource(@TempDir File dir) throws Exception {
    Project p = ProjectBuilder.builder().withProjectDir(dir).build();
    p.getPlugins().apply("java");
    p.getPlugins().apply("io.github.watchtowerobservability.watchtower");
    p.getExtensions().getByType(WatchtowerExtension.class).getRelease().set("9.9");
    ((WriteReleaseTask) p.getTasks().getByName("watchtowerWriteRelease")).write();
    String f = Files.readString(new File(dir, "build/generated/watchtower/resources/META-INF/watchtower.properties").toPath());
    assertEquals("release=9.9\n", f);
    assertNotNull(p.getTasks().findByName("processResources"));
  }

  @Test
  void helpers() {
    assertTrue(WatchtowerPlugin.isReleaseBuild("assembleFreeRelease"));
    assertTrue(WatchtowerPlugin.isReleaseBuild("bundleRelease"));
    assertFalse(WatchtowerPlugin.isReleaseBuild("assembleDebug"));
    assertEquals(WatchtowerClient.debugId("a".getBytes()), WatchtowerClient.debugId("a".getBytes()));
    assertEquals("x/mapping.txt", UploadTask.mappingName(new File("o/x/mapping.txt")));
  }
}
