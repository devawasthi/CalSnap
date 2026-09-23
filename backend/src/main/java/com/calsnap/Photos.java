package com.calsnap;

import io.micronaut.context.annotation.Context;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.Connection;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Context
public class Photos {
  final Config config;
  final Path local;
  final String mode;
  final Db db;

  public Photos(Config config, Db db) {
    this.config = config;
    this.db = db;
    mode = config.get("PHOTO_STORAGE", config.local() ? "local" : "database");
    local = Path.of(config.get("PHOTO_DIR", ".local/photos"));
    if (!mode.equals("local") && !mode.equals("database"))
      throw new IllegalArgumentException("PHOTO_STORAGE must be local or database");
  }

  public String put(UUID owner, byte[] bytes) throws Exception {
    if (mode.equals("database"))
      return db.tx(c -> put(c, owner, bytes));
    return put(null, owner, bytes);
  }

  public String put(Connection connection, UUID owner, byte[] bytes) throws Exception {
    String key = owner + "/" + UUID.randomUUID() + ".jpg";
    if (mode.equals("local")) {
      Path p = local.resolve(key);
      Files.createDirectories(p.getParent());
      Files.write(p, bytes);
    } else if (mode.equals("database")) {
      if (connection == null) throw new IllegalStateException("Database photo write requires a transaction");
      Db.execute(
          connection,
          "INSERT INTO photo_objects(owner_id,object_key,content) VALUES(?,?,?)",
          owner,
          key,
          bytes);
    }
    return key;
  }

  public void delete(String key) throws Exception {
    if (mode.equals("database")) {
      db.tx(
          c -> {
            delete(c, key);
            return null;
          });
    } else delete(null, key);
  }

  public void delete(Connection connection, String key) throws Exception {
    if (key == null || key.isBlank()) return;
    if (mode.equals("local")) Files.deleteIfExists(local.resolve(key));
    else if (mode.equals("database")) {
      if (connection == null) throw new IllegalStateException("Database photo delete requires a transaction");
      Db.execute(connection, "DELETE FROM photo_objects WHERE object_key=?", key);
    }
  }

  public void deleteOwner(UUID owner) throws Exception {
    if (mode.equals("database")) {
      db.tx(
          c -> {
            deleteOwner(c, owner);
            return null;
          });
    } else deleteOwner(null, owner);
  }

  public void deleteOwner(Connection connection, UUID owner) throws Exception {
    if (mode.equals("local")) {
      Path folder = local.resolve(owner.toString());
      if (Files.exists(folder))
        try (var files = Files.walk(folder)) {
          for (Path p : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        }
    } else if (mode.equals("database")) {
      if (connection == null) throw new IllegalStateException("Database photo delete requires a transaction");
      Db.execute(connection, "DELETE FROM photo_objects WHERE owner_id=?", owner);
    }
  }

  public String url(String key) throws Exception {
    if (key == null || key.isBlank()) return "";
    long expiry = Instant.now().plusSeconds(300).getEpochSecond();
    return "/photos/" + key + "?expires=" + expiry + "&signature=" + signature(key, expiry);
  }

  String signature(String key, long expires) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(config.secret(), "HmacSHA256"));
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(mac.doFinal((key + ":" + expires).getBytes(StandardCharsets.UTF_8)));
  }

  public byte[] readLocal(UUID user, UUID image, long expires, String signature) throws Exception {
    return read(user, image, expires, signature);
  }

  public byte[] read(UUID user, UUID image, long expires, String signature) throws Exception {
    String key = user + "/" + image + ".jpg";
    if (expires < Instant.now().getEpochSecond()
        || expires > Instant.now().plusSeconds(301).getEpochSecond()
        || !MessageDigest.isEqual(
            signature(key, expires).getBytes(StandardCharsets.UTF_8),
            signature.getBytes(StandardCharsets.UTF_8)))
      throw new io.micronaut.http.exceptions.HttpStatusException(
          io.micronaut.http.HttpStatus.NOT_FOUND, "Photo unavailable");
    if (mode.equals("local")) return Files.readAllBytes(local.resolve(key));
    return db.tx(
        c ->
            (byte[])
                Db.one(
                        c,
                        "SELECT content FROM photo_objects WHERE owner_id=? AND object_key=?",
                        user,
                        key)
                    .get("content"));
  }
}
