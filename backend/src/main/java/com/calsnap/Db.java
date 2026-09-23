package com.calsnap;

import io.micronaut.http.*;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;
import java.sql.*;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;

@Singleton
public class Db {
  private final DataSource source;

  public Db(DataSource source) {
    this.source = source;
  }

  @FunctionalInterface
  public interface Work<T> {
    T run(Connection c) throws Exception;
  }

  public <T> T tx(Work<T> work) {
    try (Connection c = source.getConnection()) {
      c.setAutoCommit(false);
      try {
        T result = work.run(c);
        c.commit();
        return result;
      } catch (Exception e) {
        c.rollback();
        if (e instanceof RuntimeException r) throw r;
        throw new IllegalStateException("Database operation failed", e);
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Database unavailable", e);
    }
  }

  public static List<Map<String, Object>> rows(Connection c, String sql, Object... args)
      throws SQLException {
    try (PreparedStatement p = prepare(c, sql, args);
        ResultSet r = p.executeQuery()) {
      List<Map<String, Object>> out = new ArrayList<>();
      while (r.next()) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 1; i <= r.getMetaData().getColumnCount(); i++) {
          Object v = r.getObject(i);
          if (v instanceof Timestamp t) v = t.toInstant().toString();
          if (v instanceof java.sql.Date d) v = d.toLocalDate().toString();
          row.put(r.getMetaData().getColumnLabel(i), v);
        }
        out.add(row);
      }
      return out;
    }
  }

  public static int execute(Connection c, String sql, Object... args) throws SQLException {
    try (PreparedStatement p = prepare(c, sql, args)) {
      return p.executeUpdate();
    }
  }

  private static PreparedStatement prepare(Connection c, String sql, Object... args)
      throws SQLException {
    PreparedStatement p = c.prepareStatement(sql);
    for (int i = 0; i < args.length; i++) {
      Object v = args[i];
      if (v instanceof Instant t) v = Timestamp.from(t);
      p.setObject(i + 1, v);
    }
    return p;
  }

  public static Map<String, Object> one(Connection c, String sql, Object... args)
      throws SQLException {
    var rows = rows(c, sql, args);
    if (rows.isEmpty()) throw new HttpStatusException(HttpStatus.NOT_FOUND, "Not found");
    return rows.getFirst();
  }

  public static void lockUser(Connection c, UUID id) throws SQLException {
    one(c, "SELECT id FROM users WHERE id=? FOR UPDATE", id);
  }
}
