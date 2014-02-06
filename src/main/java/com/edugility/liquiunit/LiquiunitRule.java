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

import java.util.Iterator;

import javax.sql.DataSource;

import liquibase.Liquibase;

import liquibase.changelog.ChangeLogHistoryService;
import liquibase.changelog.ChangeLogHistoryServiceFactory;
import liquibase.changelog.StandardChangeLogHistoryService;

import liquibase.database.Database;
import liquibase.database.DatabaseFactory;

import liquibase.database.jvm.JdbcConnection;

import liquibase.exception.LiquibaseException;

import liquibase.logging.LogFactory;
import liquibase.logging.Logger;

import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.CompositeResourceAccessor;
import liquibase.resource.FileSystemResourceAccessor;
import liquibase.resource.ResourceAccessor;

import org.junit.rules.ExternalResource;

import org.junit.runner.Description;

import org.junit.runners.model.Statement;

public class LiquiunitRule extends ExternalResource {

  protected final Logger logger;

  private final DataSource dataSource;

  private final Object id;

  private Description description;

  private String username;

  private String password;

  private String changeLogResourceName;

  private Connection c;

  private Liquibase liquibase;

  private Iterable<? extends String> contexts;

  private ResourceAccessor resourceAccessor;

  public LiquiunitRule(final DataSource dataSource) {
    this(dataSource, "sa", "");
  }

  public LiquiunitRule(final DataSource dataSource, final String username, final String password) {
    super();
    this.logger = LogFactory.getInstance().getLog();
    assert this.logger != null;
    this.dataSource = dataSource;
    this.id = Integer.valueOf(System.identityHashCode(this));
    this.setChangeLogResourceName("changelog.xml");
    this.setResourceAccessor(new CompositeResourceAccessor(new URLResourceAccessor(Thread.currentThread().getContextClassLoader()), new FileSystemResourceAccessor(System.getProperty("user.dir"))));
    this.setUsername(username);
    this.setPassword(password);
  }

  public String getUsername() {
    return this.username;
  }

  public void setUsername(final String username) {
    this.username = username;
  }

  public String getPassword() {
    return this.password;
  }

  public void setPassword(final String password) {
    this.password = password;
  }

  public String getChangeLogResourceName() {
    return this.changeLogResourceName;
  }

  public void setChangeLogResourceName(final String name) {
    this.changeLogResourceName = name;
  }

  public Iterable<? extends String> getContexts() {
    return this.contexts;
  }

  public void setContexts(final Iterable<? extends String> contexts) {
    this.contexts = contexts;
  }

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

        // Get all the changelog contexts and turn them into a
        // comma-separated String
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
        
        try {
          this.liquibase.update(sb == null ? null : sb.toString());
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
   */
  protected Liquibase createLiquibase(final Database database) throws LiquibaseException {
    final Liquibase liquibase;
    final String changeLogResourceName = this.getChangeLogResourceName();
    if (changeLogResourceName == null) {
      liquibase = null;
    } else {
      final ClassLoader tcl = Thread.currentThread().getContextClassLoader();
      if (tcl == null) {
        liquibase = null;
      } else {
        final Object url = tcl.getResource(changeLogResourceName);
        if (url == null) {
          liquibase = null;
        } else {
          liquibase = new Liquibase(changeLogResourceName, this.getResourceAccessor(), database);
        }
      }
    }
    return liquibase;
  }

  public ResourceAccessor getResourceAccessor() {
    return this.resourceAccessor;
  }

  public void setResourceAccessor(final ResourceAccessor resourceAccessor) {
    this.resourceAccessor = resourceAccessor;
  }

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

  @Override
  public Statement apply(final Statement base, final Description description) {
    this.description = description;
    return super.apply(base, description);
  }

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

  public static final Object pid() {
    final Object pid;
    final String name = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
    if (name == null) {
      pid = null;
    } else {
      pid = name.substring(0, name.indexOf('@'));
    }
    return pid;
  }

  /*
   * Test ordering seems to be:
   *
   * Invoke test class' beforeClass()
   * Create test instance
   * Create @Rule-annotated rule instance
   * Invoke rule1.apply()
   * Invoke rule2.apply()
   * Invoke rule2.before()
   * Invoke rule1.before()
   * Invoke test @Before
   * Invoke test method itself
   * Invoke test @After
   * Invoke rule1.after()
   * Invoke rule2.after()
   * Invoke test class' afterClass()
   */

}
