package io.watchtower.gradle;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

/**
 * Talks to the Watchtower artifacts API (the same endpoints as {@code wtctl}): create the release,
 * then {@code POST /v1/projects/{id}/artifacts?kind=&name=&release=} with a gzip body.
 * The key is sent as a Bearer header and never put in a message.
 */
final class WatchtowerClient {
  private final String api;
  private final String project;
  private final String key;
  private final HttpClient http = HttpClient.newHttpClient();

  WatchtowerClient(String api, String project, String key) {
    if (api == null || api.isEmpty() || project == null || project.isEmpty() || key == null || key.isEmpty()) {
      throw new IllegalArgumentException("watchtower.url (WT_API), watchtower.project (WT_PROJECT) and the auth token (WT_API_KEY) are required");
    }
    this.api = api.replaceAll("/+$", "");
    this.project = project;
    this.key = key;
  }

  /** ProGuard/R8 maps carry no id the API can read, so use a stable one derived from the content. */
  static String debugId(byte[] data) {
    return UUID.nameUUIDFromBytes(data).toString();
  }

  static String enc(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }

  void ensureRelease(String version) throws IOException, InterruptedException {
    String body = "{\"version\":\"" + version.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
    send(HttpRequest.newBuilder(URI.create(base() + "/releases"))
        .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)), "release " + version);
  }

  /** {@code name} is a slash-separated relative path; {@code debugId} may be null for files that carry their own. */
  void upload(String kind, String name, String release, String debugId, byte[] data)
      throws IOException, InterruptedException {
    ByteArrayOutputStream z = new ByteArrayOutputStream();
    try (GZIPOutputStream g = new GZIPOutputStream(z)) { g.write(data); }
    String q = "kind=" + enc(kind) + "&name=" + enc(name) + "&release=" + enc(release)
        + (debugId == null ? "" : "&debug_id=" + enc(debugId));
    send(HttpRequest.newBuilder(URI.create(base() + "/artifacts?" + q))
        .header("Content-Type", "application/octet-stream").header("Content-Encoding", "gzip")
        .POST(HttpRequest.BodyPublishers.ofByteArray(z.toByteArray())), name);
  }

  private String base() {
    return api + "/v1/projects/" + enc(project);
  }

  private void send(HttpRequest.Builder b, String what) throws IOException, InterruptedException {
    HttpResponse<String> r = http.send(b.header("Authorization", "Bearer " + key).build(),
        HttpResponse.BodyHandlers.ofString());
    if (r.statusCode() / 100 != 2) {
      throw new IOException("Watchtower refused " + what + ": HTTP " + r.statusCode() + " " + r.body());
    }
  }
}
