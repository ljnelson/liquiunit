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

import java.sql.Connection;
import java.sql.SQLException;

import java.util.Arrays;
import java.util.Iterator;

import javax.sql.DataSource;

import liquibase.Liquibase;

import liquibase.changelog.ChangeLogHistoryService;
import liquibase.changelog.ChangeLogHistoryServiceFactory;
import liquibase.changelog.StandardChangeLogHistoryService;

import liquibase.database.Database;
import liquibase.database.DatabaseConnection; // for javadoc only
import liquibase.database.DatabaseFactory;

import liquibase.database.jvm.JdbcConnection;

import liquibase.exception.LiquibaseException;

import liquibase.logging.LogFactory;
import liquibase.logging.Logger;

import liquibase.resource.CompositeResourceAccessor;
import liquibase.resource.FileSystemResourceAccessor;
import liquibase.resource.ResourceAccessor;

import org.junit.rules.ExternalResource;

/**
 * An {@link ExternalResource} that performs a <a
 * href="http://liquibase.org/">Liquibase</a> {@linkplain
 * Liquibase#update(String) update} operation before a given test run.
 *
 * @author <a href="http://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see #before()
 * 
 * @see Liquibase#update(String)
 *
 * @see ExternalResource
 */
public class LiquiunitRule extends ExternalResource {

  /**
   * The {@link Logger} to use for debugging and tracing purposes.
   *
   * <p>This field is never {@code null}.</p>
   */
  protected final Logger logger;

  /**
   * The {@link DataSource} to use to {@linkplain
   * DataSource#getConnection(String, String) acquire a
   * <code>Connection</code>}.
   *
   * <p>This field is never {@code null}.</p>
   *
   * @see DataSource#getConnection(String, String)
   */
  private final DataSource dataSource;

  /**
   * The username supplied to the {@link
   * DataSource#getConnection(String, String)} invocation.
   *
   * <p>This field may be {@code null}.</p>
   *
   * @see DataSource#getConnection(String, String)
   *
   * @see #LiquiunitRule(DataSource, String, String, String[])
   *
   * @see #setUsername(String)
   *
   * @see #getUsername()
   */
  private String username;

  /**
   * The password supplied to the {@link
   * DataSource#getConnection(String, String)} invocation.
   *
   * <p>This field may be {@code null}.</p>
   *
   * @see DataSource#getConnection(String, String)
   *
   * @see #LiquiunitRule(DataSource, String, String, String[])
   *
   * @see #setPassword(String)
   *
   * @see #getPassword()
   */
  private String password;

  /**
   * The classpath resource name of the Liquibase <a
   * href="http://www.liquibase.org/documentation/databasechangelog.html">changelog</a>
   * to use.
   *
   * <p>This field may be {@code null}.</p>
   *
   * @see #changeLogResourceExists()
   *
   * @see #getChangeLogResourceName()
   *
   * @see #setChangeLogResourceName(String)
   */
  private String changeLogResourceName;

  /**
   * The {@link Connection} {@linkplain
   * DataSource#getConnection(String, String) acquired} from the
   * {@link DataSource} supplied at {@linkplain
   * #LiquiunitRule(DataSource, String, String, String[]) construction
   * time}.
   *
   * <p>This field may be {@code null}.</p>
   *
   * @see #LiquiunitRule(DataSource, String, String, String[])
   *
   * @see DataSource#getConnection(String, String)
   */
  private Connection c;

  /**
   * The {@link Liquibase} instance created by the {@link
   * #createLiquibase(Database)} method.
   *
   * <p>This field may be {@code null}.</p>
   *
   * @see #createLiquibase(Database)
   *
   * @see Liquibase
   */
  private Liquibase liquibase;

  /**
   * An {@link Iterable} of Liquibase contexts supplied at {@linkplain
   * #LiquiunitRule(DataSource, String, String, String[]) construction
   * time} that will be converted to a comma-separated {@link String}
   * and supplied to the {@linkplain Liquibase#update(String)} method.
   *
   * <p>This field may be {@code null}.</p>
   *
   * @see #setContexts(Iterable)
   *
   * @see #getContexts()
   *
   * @see Liquibase#update(String)
   */
  private Iterable<? extends String> contexts;

  /**
   * A {@link ResourceAccessor} that will be supplied to the relevant
   * {@linkplain Liquibase#Liquibase(String, ResourceAccessor,
   * Database) constructor}.
   *
   * <p>This field is initialized by the {@link
   * #LiquiunitRule(DataSource, String, String, String[])} constructor
   * to be a {@link CompositeResourceAccessor} that delegates first to
   * a {@link URLResourceAccessor} and then to a {@link
   * FileSystemResourceAccessor}.</p>
   *
   * <p>This field may be {@code null}.</p>
   *
   * @see #getResourceAccessor()
   *
   * @see #setResourceAccessor(ResourceAccessor)
   *
   * @see #LiquiunitRule(DataSource, String, String, String[])
   *
   * @see ResourceAccessor
   *
   * @see Liquibase#Liquibase(String, ResourceAccessor, Database)
   */
  private ResourceAccessor resourceAccessor;

  /**
   * Creates a new {@link LiquiunitRule}.
   *
   * <p>This constructor calls the {@link #LiquiunitRule(DataSource,
   * String, String, String[])} constructor passing the supplied
   * {@link DataSource}, "{@code sa}" and "" (the {@linkplain
   * String#isEmpty() empty} {@link String}) as parameter values.</p>
   *
   * @param dataSource the {@link DataSource} to use to {@linkplain
   * DataSource#getConnection(String, String) acquire a
   * <code>Connection</code>}; may be {@code null} in which case this
   * {@link LiquiunitRule} will effectively do nothing
   *
   * @see #LiquiunitRule(DataSource, String, String, String[])
   */
  public LiquiunitRule(final DataSource dataSource) {
    this(dataSource, "sa", "");
  }

  /**
   * Creates a new {@link LiquiunitRule}.
   *
   * <p>The new {@link LiquiunitRule} will have a {@linkplain
   * #getChangeLogResourceName changelog resource name} of "{@code
   * changelog.xml}", and a {@link #getResourceAccessor()
   * ResourceAccessor} {@linkplain
   * #setResourceAccessor(ResourceAccessor) initialized} with the
   * following code:</p>
   *
   * <!-- Formatting here is important for proper Javadoc output -->
   * <blockquote><pre>new CompositeResourceAccessor(new URLResourceAccessor(Thread.currentThread().getContextClassLoader()), 
   *                              new FileSystemResourceAccessor(System.getProperty("user.dir")))</pre></blockquote>
   *
   * @param dataSource the {@link DataSource} to use to {@linkplain
   * DataSource#getConnection(String, String) acquire a
   * <code>Connection</code>}; may be {@code null} in which case this
   * {@link LiquiunitRule} will effectively do nothing
   *
   * @param username the username to use during {@linkplain
   * DataSource#getConnection(String, String) <code>Connection</code>
   * acquisition}; may be {@code null}
   *
   * @param password the password to use during {@linkplain
   * DataSource#getConnection(String, String) <code>Connection</code>
   * acquisition}; may be {@code null}
   *
   * @param contexts a variable number of Liquibase <a
   * href="http://www.liquibase.org/documentation/contexts.html">contexts</a>;
   * may be {@code null}
   *
   * @see DataSource#getConnection(String, String)
   *
   * @see #setUsername(String)
   *
   * @see #setPassword(String)
   *
   * @see #setContexts(Iterable)
   *
   * @see #setResourceAccessor(ResourceAccessor)
   *
   * @see #setChangeLogResourceName(String)
   */
  public LiquiunitRule(final DataSource dataSource, final String username, final String password, final String... contexts) {
    super();
    this.logger = LogFactory.getInstance().getLog("liquiunit");
    assert this.logger != null;
    this.dataSource = dataSource;
    this.setUsername(username);
    this.setPassword(password);
    this.setChangeLogResourceName("changelog.xml");
    this.setResourceAccessor(new CompositeResourceAccessor(new URLResourceAccessor(Thread.currentThread().getContextClassLoader()), new FileSystemResourceAccessor(System.getProperty("user.dir"))));
    if (contexts != null && contexts.length > 0) {
      this.setContexts(Arrays.asList(contexts));
    }
  }

  /**
   * Returns the username used during {@linkplain
   * DataSource#getConnection(String, String) <code>Connection</code>
   * acquisition}.
   *
   * <p>This method may return {@code null}.</p>
   *
   * @return the username used during {@linkplain
   * DataSource#getConnection(String, String) <code>Connection</code>
   * acquisition}, or {@code null}
   *
   * @see #setUsername(String)
   *
   * @see #LiquiunitRule(DataSource, String, String, String[])
   */
  public String getUsername() {
    return this.username;
  }

  /**
   * Sets the username used during {@linkplain
   * DataSource#getConnection(String, String) <code>Connection</code>
   * acquisition}.
   *
   * @param username the username to be used during {@linkplain
   * DataSource#getConnection(String, String) <code>Connection</code>
   * acquisition}; may be {@code null}
   *
   * @see #getUsername()
   *
   * @see #LiquiunitRule(DataSource, String, String, String[])
   */
  public void setUsername(final String username) {
    this.username = username;
  }

  /**
   * Returns the password used during {@linkplain
   * DataSource#getConnection(String, String) <code>Connection</code>
   * acquisition}.
   *
   * <p>This method may return {@code null}.</p>
   *
   * @return the password used during {@linkplain
   * DataSource#getConnection(String, String) <code>Connection</code>
   * acquisition}, or {@code null}
   *
   * @see #setPassword(String)
   *
   * @see #LiquiunitRule(DataSource, String, String, String[])
   */
  public String getPassword() {
    return this.password;
  }

  /**
   * Sets the password used during {@linkplain
   * DataSource#getConnection(String, String) <code>Connection</code>
   * acquisition}.
   *
   * @param password the password to be used during {@linkplain
   * DataSource#getConnection(String, String) <code>Connection</code>
   * acquisition}; may be {@code null}
   *
   * @see #getPassword()
   *
   * @see #LiquiunitRule(DataSource, String, String, String[])
   */
  public void setPassword(final String password) {
    this.password = password;
  }

  /**
   * Returns the {@linkplain ClassLoader#getResource(String) classpath
   * resource name} of the <a
   * href="http://www.liquibase.org/documentation/databasechangelog.html">changelog</a>
   * that the associated {@linkplain #createLiquibase(Database)
   * <code>Liquibase</code> instance} should use during its
   * {@linkplain Liquibase#update(String) update operation}.
   *
   * <p>This method may return {@code null}.</p>
   *
   * <p>At {@linkplain #LiquiunitRule(DataSource, String, String,
   * String[]) construction time}, this property is set to "{@code
   * changelog.xml}".</p>
   *
   * @return the {@linkplain ClassLoader#getResource(String) classpath
   * resource name} of the <a
   * href="http://www.liquibase.org/documentation/databasechangelog.html">changelog</a>
   * that the associated {@linkplain #createLiquibase(Database)
   * <code>Liquibase</code> instance} should use during its
   * {@linkplain Liquibase#update(String) update operation}, or {@code
   * null}
   *
   * @see #setChangeLogResourceName(String)
   *
   * @see #LiquiunitRule(DataSource, String, String, String[])
   */
  public String getChangeLogResourceName() {
    return this.changeLogResourceName;
  }

  /**
   * Sets the {@linkplain ClassLoader#getResource(String) classpath
   * resource name} of the <a
   * href="http://www.liquibase.org/documentation/databasechangelog.html">changelog</a>
   * that the associated {@linkplain #createLiquibase(Database)
   * <code>Liquibase</code> instance} should use during its
   * {@linkplain Liquibase#update(String) update operation}.
   *
   * @param name the {@linkplain ClassLoader#getResource(String)
   * classpath resource name} of the <a
   * href="http://www.liquibase.org/documentation/databasechangelog.html">changelog</a>
   * that the associated {@linkplain #createLiquibase(Database)
   * <code>Liquibase</code> instance} should use during its
   * {@linkplain Liquibase#update(String) update operation}; may be
   * {@code null}
   *
   * @see #getChangeLogResourceName()
   */
  public void setChangeLogResourceName(final String name) {
    this.changeLogResourceName = name;
  }

  /**
   * Returns an {@link Iterable} of Liquibase <a
   * href="http://www.liquibase.org/documentation/contexts.html">contexts</a>
   * for use during the {@linkplain Liquibase#update(String) update}
   * operation.
   *
   * <p>This method may return {@code null}.</p>
   *
   * @return an {@link Iterable} of {@link String}s, or {@code null}
   *
   * @see #setContexts(Iterable)
   *
   * @see Liquibase#update(String)
   */
  public Iterable<? extends String> getContexts() {
    return this.contexts;
  }

  /**
   * Sets the {@link Iterable} representing the Liquibase <a
   * href="http://www.liquibase.org/documentation/contexts.html">contexts</a>
   * for use during the {@linkplain Liquibase#update(String) update}
   * operation.
   *
   * @param contexts an {@link Iterable} of {@link String}s
   * representing the Liquibase <a
   * href="http://www.liquibase.org/documentation/contexts.html">contexts</a>
   * for use during the {@linkplain Liquibase#update(String) update}
   * operation; may be {@code null}
   *
   * @see #getContexts()
   *
   * @see Liquibase#update(String)
   */
  public void setContexts(final Iterable<? extends String> contexts) {
    this.contexts = contexts;
  }

  /**
   * Transforms the return value of the {@link #getContexts()} into a
   * comma-separated {@link String} suitable for passing to the {@link
   * Liquibase#update(String)} method and returns it.
   *
   * @return a comma-separated {@link String} of Liquibase <a
   * href="http://www.liquibase.org/documentation/contexts.html">contexts</a>,
   * or {@code null}
   *
   * @see #getContexts()
   *
   * @see #setContexts(Iterable)
   *
   * @see Liquibase#update(String)
   */
  private final String getContextsString() {
    final StringBuilder sb;
    final Iterable<? extends String> contexts = this.getContexts();
    if (contexts == null) {
      sb = null;
    } else {        
      final Iterator<? extends String> iterator = contexts.iterator();
      if (iterator == null) {
        sb = null;
      } else {
        sb = new StringBuilder();
        while (iterator.hasNext()) {
          final String context = iterator.next();
          if (context != null) {
            sb.append(context);
            if (iterator.hasNext()) {
              sb.append(",");
            }
          }
        }
      }
    }

    final String returnValue;
    if (sb == null) {
      returnValue = null;
    } else {
      returnValue = sb.toString();
    }
    return returnValue;
  }

  /**
   * Overrides the {@link ExternalResource#before()} method to
   * {@linkplain DataSource#getConnection(String, String) acquire a
   * <code>Connection</code>}, {@linkplain
   * DatabaseFactory#findCorrectDatabaseImplementation(DatabaseConnection)
   * find the proper Liquibase <code>Database</code> implementation},
   * {@linkplain #createLiquibase(Database) create a
   * <code>Liquibase</code> instance} and, using it, {@linkplain
   * Liquibase#update(String) update} the backing database using the
   * {@linkplain #getChangeLogResourceName() affiliated changelog}.
   *
   * @exception LiquibaseException if there was a Liquibase-related
   * error
   *
   * @exception SQLException if there was a database-related error
   * 
   * @see DataSource#getConnection(String, String)
   *
   * @see #createLiquibase(Database)
   *
   * @see #shouldUpdate(Liquibase)
   *
   * @see #getContexts()
   *
   * @see Liquibase#update(String)
   */
  @Override
  protected void before() throws LiquibaseException, SQLException {
    if (this.dataSource != null) {

      this.c = this.dataSource.getConnection(this.getUsername(), this.getPassword());
      if (this.c == null) {
        throw new IllegalStateException("this.dataSource.getConnection()", new NullPointerException("this.dataSource.getConnection()"));
      } else if (!this.c.isValid(0)) {
        throw new IllegalStateException("!this.c.isValid()");
      }

      final DatabaseFactory databaseFactory = DatabaseFactory.getInstance();
      assert databaseFactory != null;
      final Database database = databaseFactory.findCorrectDatabaseImplementation(new JdbcConnection(this.c));

      this.liquibase = this.createLiquibase(database);
      if (this.liquibase != null && this.shouldUpdate(liquibase)) {
        try {
          this.liquibase.update(this.getContextsString());
        } finally {
          this.liquibase.forceReleaseLocks();
        }
      }
    }
  }

  /**
   * Tries to {@linkplain ClassLoader#getResource(String) load} the
   * {@linkplain #getChangeLogResourceName() classpath resource
   * identifying a Liquibase changelog}, and, if successful, creates a
   * new {@link Liquibase} instance initialized to read from it, and
   * returns it.
   *
   * <p>This method may return {@code null}.</p>
   *
   * @param database the {@link Database} to operate on; must not be
   * {@code null}
   *
   * @return a new configured {@link Liquibase} instance, or {@code
   * null} if there is no changelog to read and hence no reason to
   * construct a {@link Liquibase} instance
   *
   * @exception LiquibaseException if a problem occurred while
   * constructing the new {@link Liquibase} instance
   *
   * @see #changeLogResourceExists()
   *
   * @see #getResourceAccessor()
   */
  protected Liquibase createLiquibase(final Database database) throws LiquibaseException {
    final Liquibase liquibase;
    if (changeLogResourceExists()) {
      liquibase = null;
    } else {
      liquibase = new Liquibase(changeLogResourceName, this.getResourceAccessor(), database);
    }
    return liquibase;
  }

  /**
   * Returns {@code true} if the <a
   * href="http://www.liquibase.org/documentation/databasechangelog.html">changelog</a>
   * identified by the return value of the {@link
   * #getChangeLogResourceName()} method actually exists.
   *
   * @return {@code true} if the <a
   * href="http://www.liquibase.org/documentation/databasechangelog.html">changelog</a>
   * identified by the return value of the {@link
   * #getChangeLogResourceName()} method actually exists; {@code
   * false} otherwise
   *
   * @see #getChangeLogResourceName()
   */
  protected boolean changeLogResourceExists() {
    final boolean returnValue;
    final String changeLogResourceName = this.getChangeLogResourceName();
    if (changeLogResourceName == null) {
      returnValue = false;
    } else {
      ClassLoader cl = Thread.currentThread().getContextClassLoader();
      if (cl == null) {
        cl = ClassLoader.getSystemClassLoader();
        if (cl == null) {
          cl = this.getClass().getClassLoader();
        }
      }
      returnValue = cl != null && cl.getResource(changeLogResourceName) != null;
      this.logger.debug("ChangeLog \"" + changeLogResourceName + "\" exists? " + returnValue);
    }
    return returnValue;
  }

  /**
   * Returns the {@link ResourceAccessor} associated with this {@link
   * LiquiunitRule} that will be used by the {@link
   * #createLiquibase(Database)} method while creating a {@link
   * Liquibase} instance.
   *
   * <p>This method may return {@code null}.</p>
   *
   * <p>A newly {@linkplain #LiquiunitRule(DataSource, String, String,
   * String[]) created} {@link LiquiunitRule} will, by default, have a
   * {@link ResourceAccessor} {@linkplain
   * #setResourceAccessor(ResourceAccessor) initialized} with the
   * following code:</p>
   *
   * <!-- Formatting here is important for proper Javadoc output -->
   * <blockquote><pre>new CompositeResourceAccessor(new URLResourceAccessor(Thread.currentThread().getContextClassLoader()), 
   *                              new FileSystemResourceAccessor(System.getProperty("user.dir")))</pre></blockquote>
   *
   * @return a {@link ResourceAccessor}, or {@code null}
   *
   * @see #setResourceAccessor(ResourceAccessor)
   */
  public ResourceAccessor getResourceAccessor() {
    return this.resourceAccessor;
  }

  /**
   * Sets the {@link ResourceAccessor} associated with this {@link
   * LiquiunitRule} that will be used by the {@link
   * #createLiquibase(Database)} method while creating a {@link
   * Liquibase} instance.
   *
   * @param resourceAccessor a {@link ResourceAccessor}; may be {@code
   * null}
   *
   * @see #getResourceAccessor()
   */
  public void setResourceAccessor(final ResourceAccessor resourceAccessor) {
    this.resourceAccessor = resourceAccessor;
  }

  /**
   * Returns {@code true} if the supplied {@link Liquibase} instance
   * should in fact have its {@link Liquibase#update(String)} method
   * called.
   *
   * <p>This implementation returns {@code true} if the {@link
   * Liquibase#SHOULD_RUN_SYSTEM_PROPERTY} system property is
   * non-{@code null} and set to something other than {@code false}
   * and if the supplied {@link Liquibase} instance is non-{@code
   * null} and {@linkplain
   * StandardChangeLogHistoryService#hasDatabaseChangeLogTable() is
   * connected to a database that does not yet have a
   * <code>DATABASECHANGELOG</code> table} in it.</p>
   *
   * @param liquibase the {@link Liquibase} instance to check; may be
   * {@code null} in which case {@code false} will be returned
   *
   * @return {@code true} if the supplied {@link Liquibase} instance
   * should in fact have its {@link Liquibase#update(String)} method
   * called
   *
   * @exception LiquibaseException if there was a Liquibase-related
   * error
   *
   * @exception SQLException if there was a database-related error
   *
   * @see Liquibase#update(String)
   */
  protected boolean shouldUpdate(final Liquibase liquibase) throws LiquibaseException, SQLException {
    boolean returnValue = liquibase != null && !"false".equals(System.getProperty(Liquibase.SHOULD_RUN_SYSTEM_PROPERTY));
    if (returnValue) {
      final Database database = liquibase.getDatabase();
      if (database != null) {
        final ChangeLogHistoryService changeLogHistoryService = ChangeLogHistoryServiceFactory.getInstance().getChangeLogService(database);
        returnValue = !(changeLogHistoryService instanceof StandardChangeLogHistoryService) || !((StandardChangeLogHistoryService)changeLogHistoryService).hasDatabaseChangeLogTable();
      }
    }
    return returnValue;
  }

  /**
   * Overrides the {@link ExternalResource#after()} method to
   * {@linkplain Connection#close() close} the {@link Connection} that
   * was {@linkplain DataSource#getConnection(String, String)
   * acquired} during the execution of the {@link #before()} method.
   *
   * @see #before()
   *
   * @see Connection#close()
   */
  @Override
  protected void after() {
    if (this.c != null) {
      try {
        this.c.close();
      } catch (final SQLException ignore) {
        // ignore
      }
      this.c = null;
    }
  }

}
