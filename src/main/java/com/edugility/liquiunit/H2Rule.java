/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright (c) 2013-2014 Edugility LLC.
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense and/or sell copies
 * of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT.  IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 *
 * The original copy of this license is available at
 * http://www.opensource.org/license/mit-license.html.
 */
package com.edugility.liquiunit;

import java.io.PrintWriter;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.logging.Logger;

import javax.sql.DataSource;

import org.junit.rules.ExternalResource;

import org.junit.runner.Description;

import org.junit.runners.model.Statement;

/**
 * An {@link ExternalResource} that is also a {@link DataSource} for
 * in-memory <a href="http://www.h2database.com/">H2</a> {@link
 * Connection}s, with some additional special characteristics suited
 * particularly for in-memory unit testing.
 *
 * <p>As with all {@link ExternalResource}s, this class is not safe
 * for use by multiple concurrent threads.</p>
 *
 * @author <a href="http://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see ExternalResource
 *
 * @see DataSource
 *
 * @see <a href="http://www.h2database.com/">the H2 database
 * website</a>
 */
public class H2Rule extends ExternalResource implements DataSource {

  /**
   * The {@link Description} describing the current JUnit test.
   *
   * <p>This field may be {@code null}.</p>
   */
  private volatile Description description;

  /**
   * The current, open {@link Connection} to an H2 database.
   *
   * <p>This field may be {@code null}.</p>
   */
  private volatile Connection c;

  /**
   * The username to use when {@linkplain #getConnection(String,
   * String) acquiring} {@link Connection}s.
   *
   * <p>This field may be {@code null}.</p>
   *
   * @see #getConnection(String, String)
   */
  private final String username;

  /**
   * The password to use when {@linkplain #getConnection(String,
   * String) acquiring} {@link Connection}s.
   *
   * <p>This field may be {@code null}.</p>
   *
   * @see #getPassword()
   *
   * @see #getConnection(String, String)
   */
  private final String password;

  /**
   * Any <a
   * href="http://www.h2database.com/html/features.html#execute_sql_on_connection">initialization
   * SQL</a> to be run when an H2 database is created and connected
   * to.
   *
   * <p>This field may be {@code null}.</p>
   */
  private final String initSql;

  /**
   * A potentially enormous {@link String} representing the SQL
   * required to restore a previously constructed H2 database to its
   * prior state.
   *
   * <p>This field may be {@code null}.</p>
   *
   * <p>Users of this field <strong>must</strong> synchronize on
   * {@code H2Rule.class}.</p>
   *
   * @see #getBackup()
   *
   * @see #backup()
   */
  private static volatile String backup;


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link H2Rule} with {@code sa} as the username and
   * the empty string as the password and no <a
   * href="http://www.h2database.com/html/features.html#execute_sql_on_connection">initialization
   * SQL</a>.
   *
   * <p>This constructor calls the {@link #H2Rule(String, String,
   * String)} constructor.</p>
   *
   * @see #H2Rule(String, String, String)
   */
  public H2Rule() {
    this("sa", "", null);
  }
  
  /**
   * Creates a new {@link H2Rule}.
   *
   * <p>This constructor calls the {@link #H2Rule(String, String,
   * String)} constructor.</p>
   *
   * @param username the username to use when {@linkplain
   * #getConnection(String, String) acquiring} {@link Connection}s;
   * may be {@code null}
   *
   * @param password the password to use when {@linkplain
   * #getConnection(String, String) acquiring} {@link Connection}s;
   * may be {@code null} but in normal usage probably should not be
   *
   * @see #H2Rule(String, String, String)
   */
  public H2Rule(final String username, final String password) {
    this(username, password, null);
  }

  /**
   * Creates a new {@link H2Rule}.
   *
   * @param username the username to use when {@linkplain
   * #getConnection(String, String) acquiring} {@link Connection}s;
   * may be {@code null}
   *
   * @param password the password to use when {@linkplain
   * #getConnection(String, String) acquiring} {@link Connection}s;
   * may be {@code null} but in normal usage probably should not be
   *
   * @param initSql any <a
   * href="http://www.h2database.com/html/features.html#execute_sql_on_connection">initialization
   * SQL</a> to pass to the H2 database upon initial connection; may
   * be {@code null}
   *
   * @see #getConnection(String, String)
   *
   * @see DataSource#getConnection(String, String)
   *
   * @see <a
   * href="http://www.h2database.com/html/features.html#execute_sql_on_connection">the
   * Execute SQL on Connection section of the H2 documentation</a>
   */
  public H2Rule(final String username, final String password, final String initSql) {
    super();
    this.username = username;
    this.password = password;
    this.initSql = initSql;
  }

  /**
   * Overrides the {@link ExternalResource#apply(Statement,
   * Description)} method to store the supplied {@link Description}
   * for usage by the {@link #getConnection(String, String)} method
   * internally and returns the superclass' return value.
   *
   * <p>It must be assumed that this method may return {@code null}
   * since the superclass documentation does not mention whether the
   * return value must be non-{@code null}.</p>
   *
   * @param base the {@link Statement} to decorate; the superclass
   * documentation does not define what behavior will occur if this
   * parameter is {@code null}
   *
   * @param description the {@link Description} describing the test
   * underway; the superclass documentation does not define what
   * behavior will occur if this parameter is {@code null}
   *
   * @return a {@link Statement}; possibly {@code null}
   *
   * @see #getConnection(String, String)
   *
   * @see ExternalResource#apply(Statement, Description)
   */
  @Override
  public Statement apply(final Statement base, final Description description) {
    this.description = description;
    return super.apply(base, description);
  }
  
  /**
   * {@linkplain #getConnection(String, String) Opens a new
   * <code>Connection</code>} to a thread-private H2 in-memory
   * database and restores any {@linkplain #getBackup() backup} that
   * was stored by the {@link #after()} method.
   *
   * @see #getConnection(String, String)
   *
   * @see #getBackup()
   *
   * @see #after()
   *
   * @see ExternalResource#before()
   */
  @Override
  protected void before() throws SQLException {
    this.c = this.getConnection(this.username, this.password);
    if (this.c == null) {
      throw new IllegalStateException("this.getConnection()", new NullPointerException("this.getConnection()"));
    }
    this.c.setAutoCommit(false);    
    this.initializeOrLoadPristineDatabaseState();
  }

  private final void initializeOrLoadPristineDatabaseState() throws SQLException {
    if (this.c == null) {
      throw new IllegalStateException("this.getConnection()", new NullPointerException("this.getConnection()"));
    } else if (!this.c.isValid(0)) {
      throw new IllegalStateException("this.getConnection().isValid(0)");
    }
    final String backup = this.getBackup();
    if (backup != null) {
      final java.sql.Statement statement = this.c.createStatement();
      assert statement != null;
      try {
        statement.execute(backup);
      } finally {
        if (statement != null) {
          try {
            statement.close();
          } catch (final SQLException neverMind) {
            
          }
        }
      }
    }
  }

  private final void backup() throws SQLException {
    if (this.c == null) {
      throw new IllegalStateException("this.getConnection()", new NullPointerException("this.getConnection()"));
    } else if (!this.c.isValid(0)) {
      throw new IllegalStateException("this.getConnection().isValid(0)");
    }
    final java.sql.Statement statement = this.c.createStatement();
    assert statement != null;
    final StringBuilder sb = new StringBuilder();
    ResultSet rs = null;
    try {
      rs = statement.executeQuery("SCRIPT");
      assert rs != null;
      while (rs.next()) {
        sb.append(rs.getString(1)).append("\n");
      }
    } finally {
      if (rs != null) {
        try {
          rs.close();
        } catch (final SQLException neverMind) {
          // ignore
        }
      }
      try {
        statement.close();
      } catch (final SQLException neverMind) {
        // ignore
      }
    }
    synchronized (H2Rule.class) {
      backup = sb.toString();
    }
  }

  /**
   * Returns the DDL and SQL that represents the last backup taken by
   * the {@link #after()} method as a {@link String}.
   *
   * <p>This method may return {@code null}.</p>
   *
   * @return DDL or SQL that represents the last backup taken by the
   * {@link #after()} method, or {@code null}
   *
   * @see #after()
   */
  public final String getBackup() {
    synchronized (H2Rule.class) {
      return backup;
    }
  }

  /**
   * Ensures that the current H2 database is {@linkplain #getBackup()
   * backed up} and shut down properly.
   *
   * @see #before()
   *
   * @see ExternalResource#after()
   */
  @Override
  protected void after() {
    if (this.c != null) {
      try {

        synchronized (H2Rule.class) {
          final String backup = this.getBackup();
          if (backup == null) {
            this.backup();
          }
        }

        final java.sql.Statement s = this.c.createStatement();
        assert s != null;
        try {
          s.execute("SHUTDOWN");
        } finally {
          try {
            s.close();
          } catch (final SQLException ignore) {
            // ignore
          }
        }

        this.c.close();
      } catch (final SQLException nothingToDo) {
        nothingToDo.printStackTrace();
      }
      this.c = null;
    }
    this.description = null;
  }

  /**
   * Returns the return value of the {@link
   * DriverManager#getLoginTimeout()} method.
   *
   * @return the return value of the {@link
   * DriverManager#getLoginTimeout()} method
   */
  @Override
  public final int getLoginTimeout() {
    return DriverManager.getLoginTimeout();
  }

  /**
   * Calls the {@link DriverManager#setLoginTimeout(int)} method
   * supplying it with the value of the supplied {@code timeout}
   * parameter.
   *
   * @param timeout the timeout value
   */
  @Override
  public final void setLoginTimeout(final int timeout) {
    DriverManager.setLoginTimeout(timeout);
  }

  /**
   * Returns the return value of the {@link
   * DriverManager#getLogWriter()} method.  This method may return
   * {@code null}.
   *
   * @return the return value of the {@link
   * DriverManager#getLogWriter()} method, or {@code null}
   */
  @Override
  public final PrintWriter getLogWriter() {
    return DriverManager.getLogWriter();
  }

  /**
   * Calls the {@link DriverManager#setLogWriter(PrintWriter)}
   * method, supplying it with the value of the supplied {@code
   * writer} parameter.
   *
   * @param writer the {@link PrintWriter} to which the underlying
   * {@link DriverManager} will log; may be {@code null}
   */
  @Override
  public final void setLogWriter(final PrintWriter writer) {
    DriverManager.setLogWriter(writer);
  }

  /**
   * Returns the result of invoking the {@link
   * Logger#getLogger(String)} method with an argument of "{@code
   * com.edugility}".
   *
   * @return a {@link Logger}; never {@code null}
   */
  // @Override // when Java 7 is a minimal requirement
  public Logger getParentLogger() {
    return Logger.getLogger("com.edugility");
  }

  /**
   * Returns a <strong>cached and open</strong> {@link Connection} to
   * an in-memory H2 database that is safe for use by the current
   * {@link Thread}.
   *
   * @return a non-{@code null} {@link Connection}
   *
   * @exception SQLException if a {@link Connection} could not be
   * created
   */
  @Override
  public Connection getConnection() throws SQLException {
    boolean closed = true;
    try {
      closed = this.c == null || this.c.isClosed();
    } catch (final SQLException oops) {
      closed = true;
    }
    if (closed) {
      this.c = this.getConnection(this.username, this.password);
    }
    return this.c;
  }

  /**
   * Internally uses the {@link DriverManager#getConnection(String,
   * String, String)} method to create a {@link Connection} to a new,
   * in-memory H2 database that is <a
   * href="https://groups.google.com/d/msg/h2-database/FjteyQPoiGg/IffNPLQjirAJ">safe
   * for use</a> by the current {@link Thread}, not just the current
   * JVM.
   *
   * <p>In-memory H2 databases by default are private to a given
   * {@link Connection}.  For most unit- and integration-testing
   * purposes, that is not sufficient, as often several {@link
   * Connection}s have to be opened to those databases to perform
   * various operations.  What you want is a way to have two
   * independently created {@link Connection}s connect to the same
   * in-memory H2 database instance.</p>
   *
   * <p>This is easy to do, of course&mdash;you simply name the
   * database (e.g. {@code jdbc:h2:mem:fred} instead of just {@code
   * jdbc:h2:mem:}).  But you want to isolate this name from other
   * threads and processes that might try to connect to it, so you
   * need to pick a name that is unique to the current {@link Thread}.
   * This method accomplishes just such a {@link Connection}.</p>
   *
   * <p>The JDBC URL that is used to open a {@link Connection} is
   * built according to the following template:</p>
   *
   * <blockquote><pre>jdbc:h2:mem:[TEST_NAME-]pid=PID-thread=THREAD_ID[;INIT=INIT_SQL]</pre></blockquote>
   *
   * <p>...where brackets denote optional elements, {@code TEST_NAME}
   * is the return value of the {@link Description#getDisplayName()}
   * method, {@code PID} is the return value of the {@link #pid()}
   * method, {@code THREAD_ID} is the return value of the {@link
   * Thread#getId()} method when invoked on {@linkplain
   * Thread#currentThread() current <code>Thread</code>} and {@code
   * INIT_SQL} is the initialization SQL passed to {@linkplain
   * #H2Rule(String, String, String) the constructor}.</p>
   *
   * <p>The {@link Connection} returned is guaranteed to be non-{@code
   * null} and not {@linkplain Connection#isClosed() closed}.</p>
   *
   * @param username the username to use; may be {@code null}
   *
   * @param password the password to use; may be {@code null}
   *
   * @return a non-{@code null} open {@link Connection}
   *
   * @exception SQLException if an error occurs
   *
   * @see DriverManager#getConnection(String, String, String)
   *
   * @see DriverManager#getConnection(String)
   *
   * @see <a
   * href="http://h2database.com/html/features.html#in_memory_databases">The
   * section on in-memory databases in the H2 documentation</a>
   */
  @Override
  public Connection getConnection(final String username, final String password) throws SQLException {
    final StringBuilder sb = new StringBuilder("jdbc:h2:mem:");
    if (this.description != null) {
      final String displayName = this.description.getDisplayName();
      if (displayName != null) {
        sb.append(displayName);
        sb.append("-");
      }
    }
    sb.append("pid=");
    sb.append(this.pid());
    sb.append("-thread=");
    sb.append(Thread.currentThread().getId());
    if (this.initSql != null) {
      final String sql = this.initSql.trim();
      if (sql != null && !sql.isEmpty()) {
        sb.append(";INIT=");
        sb.append(sql);
      }
    }
    final String url = sb.toString();
    if (username != null) {
      return DriverManager.getConnection(url, username, password);
    } else {
      return DriverManager.getConnection(url);
    }
  }

  /**
   * Returns {@code false} when invoked.
   *
   * @param cls a {@link Class} that is ignored
   *
   * @return {@code false} in all cases
   */
  @Override
  public final boolean isWrapperFor(final Class<?> cls) {
    return false;
  }

  /**
   * Throws a {@link SQLException} when invoked.
   *
   * @param cls a {@link Class} that is ignored
   *
   * @return {@code null} in all cases
   *
   * @exception SQLException when invoked
   */
  @Override
  public final <T> T unwrap(final Class<T> cls) throws SQLException {
    throw new SQLException(new UnsupportedOperationException("unwrap"));
  }

  /**
   * Returns the current process identifier as a {@link String}.  
   *
   * <p>This method may return {@code null} in exceptional
   * circumstances.</p>
   *
   * <p>The default implementation returns a {@link String} resulting
   * from the following invocation:</p>
   *
   * <blockquote><pre>java.lang.management.ManagementFactory.getRuntimeMXBean().getName().substring(0, name.indexOf('@'));</pre></blockquote>
   *
   * @return the current process identifier as a {@link String}, or
   * {@code null}
   *
   * @see java.lang.management.ManagementFactory#getRuntimeMXBean()
   *
   * @see java.lang.management.RuntimeMXBean#getName()
   */
  public String pid() {
    final String pid;
    final String name = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
    if (name == null) {
      pid = null;
    } else {
      pid = name.substring(0, name.indexOf('@'));
    }
    return pid;
  }

}
