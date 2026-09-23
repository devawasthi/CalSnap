package com.calsnap;

import jakarta.inject.Singleton;

@Singleton
public class Config {
  public String get(String name, String fallback) {
    return System.getenv().getOrDefault(name, fallback);
  }

  public boolean local() {
    return get("APP_ENV", "production").equals("local");
  }

  public String required(String name) {
    String s = get(name, "");
    if (s.isBlank()) throw new IllegalStateException(name + " is required");
    return s;
  }

  public String origin() {
    String configured = get("PUBLIC_ORIGIN", "");
    if (!configured.isBlank()) return configured;
    String renderHost = get("RENDER_EXTERNAL_HOSTNAME", "");
    return renderHost.isBlank() ? "http://localhost:5173" : "https://" + renderHost;
  }

  public byte[] secret() {
    String s = required("JWT_SECRET");
    if (s.length() < 32 || (!local() && s.startsWith("local-development")))
      throw new IllegalStateException("JWT_SECRET must contain at least 32 characters");
    return s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
  }
}
