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

import java.util.HashMap;
import java.util.Map;

import java.util.logging.Logger;

import javax.sql.DataSource;

import org.junit.rules.ExternalResource;

import org.junit.runner.Description;

import org.junit.runners.model.Statement;

/**
 * An {@link ExternalResource} that is also a {@link DataSource} for
 * in-memory <a href="http://www.h2database.com/">H2</a> {@link
 * Connection}s, with some additional special characteristics suited
 * particularly for thread-safe, parallel in-memory unit testing.
 *
 * <p>As with all {@link ExternalResource}s, this class is not safe
 * for use by multiple concurrent threads.</p>
 *
 * <p>Instances of this class are often passed to {@linkplain
 * LiquiunitRule#LiquiunitRule(DataSource, String[])
 * new} {@link LiquiunitRule} instances.</p>
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
   * The {@link H2Archive} used to {@linkplain
   * H2Archive#saveIfEmpty(Description, Connection) save} and
   * {@linkplain H2Archive#loadUnlessEmpty(Description, Connection)
   * load} the state of the in-memory H2 database fronted by this
   * {@link H2Rule}.
   *
   * <p>This field may be {@code null}.</p>
   *
   * @see #H2Rule(String, String, String, H2Archive)
   */
  private final H2Archive archive;


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
   * String, H2Archive)} constructor.</p>
   *
   * @see #H2Rule(String, String, String, H2Archive)
   */
  public H2Rule() {
    this("sa", "", null, new H2Archive());
  }

  /**
   * Creates a new {@link H2Rule}.
   *
   * <p>This constructor calls the {@link #H2Rule(String, String,
   * String, H2Archive)} constructor.</p>
   *
   * @param username the username to use when {@linkplain
   * #getConnection(String, String) acquiring} {@link Connection}s;
   * may be {@code null}
   *
   * @param password the password to use when {@linkplain
   * #getConnection(String, String) acquiring} {@link Connection}s;
   * may be {@code null} but in normal usage probably should not be
   *
   * @see #H2Rule(String, String, String, H2Archive)
   */
  public H2Rule(final String username, final String password) {
    this(username, password, null, new H2Archive());
  }

  /**
   * Creates a new {@link H2Rule}.
   *
   * <p>This constructor calls the {@link #H2Rule(String, String,
   * String, H2Archive)} constructor.</p>
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
   * @see #H2Rule(String, String, String, H2Archive)
   */
  public H2Rule(final String username, final String password, final String initSql) {
    this(username, password, initSql, new H2Archive());
  }

  /**
   * Creates a new {@link H2Rule}.
   *
   * <p>This constructor calls the {@link #H2Rule(String, String,
   * String, H2Archive)} constructor.</p>
   *
   * @param archive the {@link H2Archive} that can backup and restore
   * the H2 database; may be {@code null}
   *
   * @see #H2Rule(String, String, String, H2Archive)
   *
   * @see H2Archive
   */
  public H2Rule(final H2Archive archive) {
    this("sa", "", null, archive);
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
   * @param archive the {@link H2Archive} that can backup and restore
   * the H2 database; may be {@code null}
   *
   * @see #H2Rule(String, String, String, H2Archive)
   *
   * @see #getConnection(String, String)
   *
   * @see DataSource#getConnection(String, String)
   *
   * @see H2Archive
   *
   * @see <a
   * href="http://www.h2database.com/html/features.html#execute_sql_on_connection">the
   * Execute SQL on Connection section of the H2 documentation</a>
   */
  public H2Rule(final String username, final String password, final String initSql, final H2Archive archive) {
    super();
    this.username = username;
    this.password = password;
    this.initSql = initSql;
    this.archive = archive;
  }


  /*
   * Instance methods.
   */


  public Map<?, ?> getJPAProperties() {
    final Map<String, String> properties = new HashMap<String, String>(7);
    properties.put("javax.persistence.jdbc.user", this.username);
    properties.put("javax.persistence.jdbc.password", this.password);
    properties.put("javax.persistence.jdbc.driver", org.h2.Driver.class.getName());
    properties.put("javax.persistence.jdbc.url", this.getConnectionURL());
    return properties;
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
   * database and restores any state that was stored by the {@link
   * #after()} method.
   *
   * @see #getConnection(String, String)
   *
   * @see H2Archive#loadUnlessEmpty(Description, Connection)
   *
   * @see #after()
   *
   * @see ExternalResource#before()
   *
   * @exception SQLException if a database error occurs
   *
   * @exception IllegalStateException if the {@link
   * #getConnection(String, String)} method returns a {@code null}
   * {@link Connection} or if the {@link
   * #configureConnection(Connection)} method causes the {@link
   * Connection} to become {@linkplain Connection#isValid(int)
   * invalid}
   */
  @Override
  protected void before() throws SQLException {
    this.c = this.getConnection(this.username, this.password);
    if (this.c == null) {
      throw new IllegalStateException("this.getConnection()", new NullPointerException("this.getConnection()"));
    }
    this.configureConnection(this.c);
    if (!this.c.isValid(0)) {
      throw new IllegalStateException("this.getConnection().isValid(0)");
    }
    if (this.archive != null) {
      this.archive.loadUnlessEmpty(this.description, this.c);
    }
  }

  /**
   * Configures the supplied open {@link Connection}.
   *
   * <p>This implementation does nothing.</p>
   *
   * @param c the {@link Connection} to configure; must not be {@code
   * null}
   *
   * @exception SQLException if a database error occurs
   */
  protected void configureConnection(final Connection c) throws SQLException {

  }

  private final void setDbCloseDelay() throws SQLException {
    if (this.c == null) {
      throw new IllegalStateException("this.getConnection()", new NullPointerException("this.getConnection()"));
    }
    final java.sql.Statement statement = this.c.createStatement();
    assert statement != null;
    ResultSet rs = null;
    try {
      rs = statement.executeQuery("SELECT VALUE FROM INFORMATION_SCHEMA.SETTINGS WHERE NAME = 'DB_CLOSE_DELAY'");
      assert rs != null;
      Integer delay = null;
      if (rs.next()) {
        delay = rs.getInt(1);
      }
      if (rs.wasNull() || delay == null) {
        rs.close();
        statement.execute("SET DB_CLOSE_DELAY=-1");
      }
    } finally {
      if (rs != null) {
        try {
          rs.close();
        } catch (final SQLException ignore) {

        }
      }
      try {
        statement.close();
      } catch (final SQLException ignore) {

      }
    }

  }

  /**
   * Ensures that the current H2 database is {@linkplain
   * H2Archive#saveIfEmpty(Description, Connection) backed up} and <a
   * href="http://www.h2database.com/html/grammar.html?highlight=shutdown&search=shutdown#shutdown">shut
   * down properly</a>.
   *
   * @see #before()
   *
   * @see ExternalResource#after()
   */
  @Override
  protected void after() {
    if (this.c != null) {

      try {

        if (this.archive != null) {
          try {
            this.archive.saveIfEmpty(this.description, this.c);
          } catch (final SQLException oops) {
            throw new IllegalStateException(oops);
          }
        }

        java.sql.Statement s = null;
        try {
          s = this.c.createStatement();
          assert s != null;
          s.execute("SHUTDOWN");
        } catch (final SQLException shutdownProblem) {
          throw new IllegalStateException(shutdownProblem);
        } finally {
          try {
            if (s != null) {
              s.close();
            }
          } catch (final SQLException ignore) {

          }
        }

      } finally {
        try {
          this.c.close();
        } catch (final SQLException ignore) {

        }
      }

      this.c = null;
    }
    this.description = null; // XXX TODO INVESTIGATE: not sure this is proper
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
   * Returns a new, open {@link Connection} to an in-memory H2
   * database that is safe for use by the current {@link Thread}.
   *
   * @return a non-{@code null} {@link Connection}
   *
   * @exception SQLException if a {@link Connection} could not be
   * created
   */
  @Override
  public Connection getConnection() throws SQLException {
    return this.getConnection(this.username, this.password);
  }

  /**
   * Internally uses the {@link DriverManager#getConnection(String,
   * String, String)} method to create a {@link Connection} to a new,
   * in-memory H2 database that is <a
   * href="https://groups.google.com/d/msg/h2-database/FjteyQPoiGg/IffNPLQjirAJ"
   * target="_parent">safe for use</a> by the current {@link Thread},
   * not just the current JVM.
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
   * Thread#getId()} method when invoked on the {@linkplain
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
    final String url = this.getConnectionURL();
    if (username != null) {
      return DriverManager.getConnection(url, username, password);
    } else {
      return DriverManager.getConnection(url);
    }
  }

  public String getConnectionURL() {
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
    return sb.toString();
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
