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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.sql.DataSource;

import com.edugility.liquibase.URLResourceAccessor;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;

import liquibase.changelog.ChangeLogHistoryService;
import liquibase.changelog.ChangeLogHistoryServiceFactory;
import liquibase.changelog.ChangeSet;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.changelog.StandardChangeLogHistoryService;

import liquibase.database.Database;
import liquibase.database.DatabaseConnection; // for javadoc only
import liquibase.database.DatabaseFactory;

import liquibase.database.jvm.JdbcConnection;

import liquibase.exception.DatabaseException;
import liquibase.exception.LiquibaseException;

import liquibase.logging.LogFactory;
import liquibase.logging.Logger;

import liquibase.resource.CompositeResourceAccessor;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.FileSystemResourceAccessor;
import liquibase.resource.ResourceAccessor;

import org.junit.rules.ExternalResource;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

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


  /*
   * Instance fields.
   */


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
   * The classpath resource names of the Liquibase <a
   * href="http://www.liquibase.org/documentation/databasechangelog.html">changelogs</a>
   * to use.
   *
   * <p>This field may be {@code null}.</p>
   *
   * @see #changeLogResourceExists()
   *
   * @see #getChangeLogResourceNames()
   *
   * @see #setChangeLogResourceNames(Iterable)
   */
  private Iterable<? extends String> changeLogResourceNames;

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
   * #LiquiunitRule(DataSource, String[]) construction
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
   * #LiquiunitRule(DataSource, String[])} constructor
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
   * @see #LiquiunitRule(DataSource, String[])
   *
   * @see ResourceAccessor
   *
   * @see Liquibase#Liquibase(String, ResourceAccessor, Database)
   */
  private ResourceAccessor resourceAccessor;

  
  /*
   * Constructors.
   */


  /**
   * Creates a new {@link LiquiunitRule}.
   *
   * <p>The new {@link LiquiunitRule} will have {@linkplain
   * #getChangeLogResourceNames() changelog resource names} of "{@code
   * changelog.xml}" and "{@code test-changelog.xml}", and a {@link
   * #getResourceAccessor() ResourceAccessor} {@linkplain
   * #setResourceAccessor(ResourceAccessor) initialized} with the
   * following code:</p>
   *
   * <!-- Formatting here is important for proper Javadoc output -->
   * <blockquote><pre>final ResourceAccessor classLoaderAccessor = new ClassLoaderResourceAccessor(Thread.currentThread().getContextClassLoader());
   *final ResourceAccessor urlAccessor = new URLResourceAccessor(classLoaderAccessor);
   *final ResourceAccessor fileAccessor = new FileSystemResourceAccessor(System.getProperty("user.dir"));
   *new CompositeResourceAccessor(urlAccessor, classLoaderAccessor, fileAccessor);</pre></blockquote>
   *
   * <p>Consider passing an {@link H2Rule} as the value for the {@code
   * dataSource} parameter for thread-safe parallel in-memory database
   * testing scenarios.</p>
   *
   * @param dataSource the {@link DataSource} to use to {@linkplain
   * DataSource#getConnection(String, String) acquire a
   * <code>Connection</code>}; may be {@code null} in which case this
   * {@link LiquiunitRule} will effectively do nothing
   *
   * @param contexts a variable number of Liquibase <a
   * href="http://www.liquibase.org/documentation/contexts.html">contexts</a>;
   * may be {@code null}
   *
   * @see DataSource#getConnection(String, String)
   *
   * @see #setContexts(Iterable)
   *
   * @see #setResourceAccessor(ResourceAccessor)
   *
   * @see #setChangeLogResourceNames(Iterable)
   */
  public LiquiunitRule(final DataSource dataSource, final String... contexts) {
    super();
    this.logger = LogFactory.getInstance().getLog("liquiunit");
    assert this.logger != null;
    String logLevel = System.getProperty("liquiunit.logLevel", "").trim();
    if (!logLevel.isEmpty()) {
      this.logger.setLogLevel(logLevel);
    }
    logLevel = System.getProperty("liquibase.logLevel", "").trim();
    if (!logLevel.isEmpty()) {
      final Logger logger = LogFactory.getInstance().getLog();
      if (logger != null) {
        logger.setLogLevel(logLevel);
      }
    }
    this.logger.debug("Entering LiquiunitRule(DataSource, String[]); parameters: dataSource = " + dataSource + "; contexts = " + (contexts == null ? "null" : Arrays.asList(contexts)));
    this.dataSource = dataSource;
    final List<String> changeLogResourceNames = new ArrayList<String>();
    changeLogResourceNames.add("changelog.xml");
    changeLogResourceNames.add("test-changelog.xml");
    this.setChangeLogResourceNames(changeLogResourceNames);
    final ResourceAccessor classLoaderAccessor = new ClassLoaderResourceAccessor(Thread.currentThread().getContextClassLoader());
    final ResourceAccessor urlAccessor = new URLResourceAccessor(classLoaderAccessor);
    final ResourceAccessor fileAccessor = new FileSystemResourceAccessor(System.getProperty("user.dir"));
    this.setResourceAccessor(new CompositeResourceAccessor(urlAccessor, classLoaderAccessor, fileAccessor));
    if (contexts != null && contexts.length > 0) {
      this.setContexts(Arrays.asList(contexts));
    }
    this.logger.debug("Exiting LiquiunitRule(DataSource, String[])");
  }


  /*
   * Instance methods.
   */


  /**
   * Returns the {@linkplain ClassLoader#getResource(String) classpath
   * resource names} of the <a
   * href="http://www.liquibase.org/documentation/databasechangelog.html">changelogs</a>
   * that the associated {@linkplain #createLiquibase(Database)
   * <code>Liquibase</code> instance} should use during {@linkplain
   * Liquibase#update(String) update operations}.
   *
   * <p>This method may return {@code null}.</p>
   *
   * <p>At {@linkplain #LiquiunitRule(DataSource, String[])
   * construction time}, this property is set to "{@code
   * changelog.xml}" and "{@code test-changelog.xml}".</p>
   *
   * @return the {@linkplain ClassLoader#getResource(String) classpath
   * resource names} of the <a
   * href="http://www.liquibase.org/documentation/databasechangelog.html">changelogs</a>
   * that the associated {@linkplain #createLiquibase(Database)
   * <code>Liquibase</code> instance} should use during {@linkplain
   * Liquibase#update(String) update operations}, or {@code null}
   *
   * @see #setChangeLogResourceNames(Iterable)
   *
   * @see #LiquiunitRule(DataSource, String[])
   */
  public Iterable<? extends String> getChangeLogResourceNames() {
    this.logger.debug("Entering getChangeLogResourceNames()");
    this.logger.debug("Exiting getChangeLogResourceNames(); returning: " + this.changeLogResourceNames);
    return this.changeLogResourceNames;

  }

  /**
   * Sets the {@linkplain ClassLoader#getResource(String) classpath
   * resource names} of the <a
   * href="http://www.liquibase.org/documentation/databasechangelog.html">changelogs</a>
   * that the associated {@linkplain #createLiquibase(Database)
   * <code>Liquibase</code> instance} should use during {@linkplain
   * Liquibase#update(String) update operations}.
   *
   * @param names the {@linkplain ClassLoader#getResource(String)
   * classpath resource names} of the <a
   * href="http://www.liquibase.org/documentation/databasechangelog.html">changelogs</a>
   * that the associated {@linkplain #createLiquibase(Database)
   * <code>Liquibase</code> instance} should use during {@linkplain
   * Liquibase#update(String) update operations}; may be {@code null}
   *
   * @see #getChangeLogResourceNames()
   */
  public void setChangeLogResourceNames(final Iterable<? extends String> names) {
    this.logger.debug("Entering setChangeLogResourceName(Iterable); parameters: names = " + names);
    this.changeLogResourceNames = names;
    this.logger.debug("Exiting setChangeLogResourceName(Iterable)");
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
    this.logger.debug("Entering getContexts()");
    this.logger.debug("Exiting getContexts(); returning: " + this.contexts);
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
    this.logger.debug("Entering setContexts(Iterable); parameters: contexts = " + contexts);
    this.contexts = contexts;
    this.logger.debug("Exiting setContexts(Iterable)");
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
    this.logger.debug("Entering getContextsString()");
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
    this.logger.debug("Exiting getContextsString(); returning: " + returnValue);
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
   * {@linkplain #getChangeLogResourceNames() affiliated changelogs}.
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
    this.logger.debug("Entering before()");
    if (this.dataSource != null) {

      final Connection c = this.dataSource.getConnection();
      if (c == null) {
        throw new IllegalStateException("this.dataSource.getConnection()", new NullPointerException("this.dataSource.getConnection()"));
      } else if (!c.isValid(0)) {
        throw new IllegalStateException("!c.isValid()");
      }
      final JdbcConnection jc = new JdbcConnection(c);
      try {
        final DatabaseFactory databaseFactory = DatabaseFactory.getInstance();
        assert databaseFactory != null;
        final Database database = databaseFactory.findCorrectDatabaseImplementation(jc);
        
        this.liquibase = this.createLiquibase(database);
        if (this.liquibase != null && this.shouldUpdate(liquibase)) {
          try {
            this.liquibase.update(this.getContextsString());
          } finally {
            this.liquibase.forceReleaseLocks();
          }
        }
      } finally {
        try {
          jc.close();
        } catch (final DatabaseException ignore) {
          
        }
      }

    }
    this.logger.debug("Exiting before()");
  }
    
  /**
   * Tries to {@linkplain ClassLoader#getResource(String) load} the
   * {@linkplain #getChangeLogResourceNames() classpath resources
   * identifying Liquibase changelogs}, and, if successful, creates a
   * new {@link Liquibase} instance initialized to read from them, and
   * returns it.
   *
   * <p>This method may return {@code null}.</p>
   *
   * <h2>Implementation Notes</h2>
   *
   * <p>When a {@link Liquibase} instance is asked to {@linkplain
   * Liquibase#update(Contexts, LabelExpression) perform an update
   * operation}, it operates on a single {@link DatabaseChangeLog}.
   * But this class can specify {@linkplain
   * #getChangeLogResourceNames() several changelogs to read}.
   * Consequently, the {@link Liquibase} instance constructed by this
   * method uses an internally built composite {@link
   * DatabaseChangeLog} that logically aggregates all appropriate
   * changelogs by virtue of its {@link
   * DatabaseChangeLog#include(String, boolean, ResourceAccessor)}
   * method.  From the point of view of a {@link Liquibase} instance
   * constructed by this method, therefore, there is only one {@link
   * DatabaseChangeLog} in existence that has been formed by the
   * inclusion of {@linkplain #getChangeLogResourceNames() all
   * relevant existing changelogs} into one.</p>
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
   *
   * @see Liquibase
   *
   * @see Liquibase#Liquibase(DatabaseChangeLog, ResourceAccessor, Database)
   *
   * @see DatabaseChangeLog
   *
   * @see DatabaseChangeLog#include(String, boolean, ResourceAccessor)
   */
  protected Liquibase createLiquibase(final Database database) throws LiquibaseException {
    this.logger.debug("Entering createLiquibase(Database); parameters: database = " + database);
    Liquibase liquibase = null;
    if (changeLogResourceExists()) {
      final Iterable<? extends String> changeLogResourceNames = this.getChangeLogResourceNames();
      if (changeLogResourceNames != null) {
        final ResourceAccessor resourceAccessor = this.getResourceAccessor();
        AugmentableDatabaseChangeLog changeLog = null;
        for (final String changeLogResourceName : changeLogResourceNames) {
          if (changeLogResourceName != null && this.changeLogResourceExists(changeLogResourceName)) {
            if (changeLog == null) {
              assert liquibase == null;
              changeLog = this.createAugmentableDatabaseChangeLog(database, changeLogResourceName);
              liquibase = new Liquibase(changeLog, resourceAccessor, database);
              changeLog.setChangeLogParameters(liquibase.getChangeLogParameters());
            } else {
              assert liquibase != null;
              changeLog.include(changeLogResourceName, resourceAccessor);
            }
          }
        }
      }
    }
    this.logger.debug("Exiting createLiquibase(Database); returning: " + liquibase);
    return liquibase;
  }

  /**
   * Returns {@code true} if at least one of the <a
   * href="http://www.liquibase.org/documentation/databasechangelog.html">changelogs</a>
   * identified by the return value of the {@link
   * #getChangeLogResourceNames()} method actually exists.
   *
   * @return {@code true} if at least one of the <a
   * href="http://www.liquibase.org/documentation/databasechangelog.html">changelogs</a>
   * identified by the return value of the {@link
   * #getChangeLogResourceNames()} method actually exists; {@code
   * false} otherwise
   *
   * @see #getChangeLogResourceNames()
   */
  protected boolean changeLogResourceExists() {
    this.logger.debug("Entering changeLogResourceExists()");
    final boolean returnValue;
    final Iterable<? extends String> changeLogResourceNames = this.getChangeLogResourceNames();
    if (changeLogResourceNames == null) {
      returnValue = false;
    } else {
      boolean foundOne = false;
      for (final String changeLogResourceName : changeLogResourceNames) {
        if (changeLogResourceName != null) {
          foundOne = this.changeLogResourceExists(changeLogResourceName);
          if (foundOne) {
            break;
          }
        }
      }
      returnValue = foundOne;
    }
    this.logger.debug("Exiting changeLogResourceExists(); returning: " + returnValue);
    return returnValue;
  }

  protected boolean changeLogResourceExists(final String changeLogResourceName) {
    this.logger.debug("Entering changeLogResourceExists(String)");
    final boolean returnValue;
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
    }
    this.logger.debug("Exiting changeLogResourceExists(String); returning: " + returnValue);
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
   * <p>A newly {@linkplain #LiquiunitRule(DataSource, String[])
   * created} {@link LiquiunitRule} will, by default, have a {@link
   * ResourceAccessor} {@linkplain
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
    this.logger.debug("Entering getResourceAccessor()");
    this.logger.debug("Exiting getResourceAccessor(); returning: " + this.resourceAccessor);
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
    this.logger.debug("Entering setResourceAccessor(ResourceAccessor); parameters: resourceAccessor = " + resourceAccessor);
    this.resourceAccessor = resourceAccessor;
    this.logger.debug("Exiting setResourceAccessor(ResourceAccessor)");
  }

  /**
   * Returns {@code true} if the supplied {@link Liquibase} instance
   * should in fact have its {@link Liquibase#update(String)} method
   * called.
   *
   * <p>This implementation returns {@code true} if the {@code
   * liquibase.should.run} system property is non-{@code null} and set
   * to something other than {@code false} and if the supplied {@link
   * Liquibase} instance is non-{@code null} and {@linkplain
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
    this.logger.debug("Entering shouldUpdate(Liquibase); parameters: liquibase = " + liquibase);
    boolean returnValue = liquibase != null && !"false".equals(System.getProperty("liquibase.should.run"));
    if (returnValue) {
      final Database database = liquibase.getDatabase();
      if (database != null) {
        final ChangeLogHistoryService changeLogHistoryService = ChangeLogHistoryServiceFactory.getInstance().getChangeLogService(database);
        returnValue = !(changeLogHistoryService instanceof StandardChangeLogHistoryService) || !((StandardChangeLogHistoryService)changeLogHistoryService).hasDatabaseChangeLogTable();
      }
    }
    this.logger.debug("Exiting shouldUpdate(Liquibase); returning: " + returnValue);
    return returnValue;
  }

  /**
   * Creates an {@link AugmentableDatabaseChangeLog} that can
   * {@linkplain AugmentableDatabaseChangeLog#include(String,
   * ResourceAccessor)} include other changelogs.
   *
   * <p>This method is a hack that exists only because the {@link
   * DatabaseChangeLog#include(String, boolean, ResourceAccessor}
   * method is not {@code public}.</p>
   *
   * @param database the {@link Database} to use; may be {@code null}
   *
   * @param changeLogResourceName the name of the changelog to parse;
   * may be {@code null}
   *
   * @return a new, non-{@code null} {@link
   * AugmentableDatabaseChangeLog}
   *
   * @exception LiquibaseException if an error occurs
   *
   * @see DatabaseChangeLog
   *
   * @see DatabaseChangeLog#include(String, boolean, ResourceAccessor)
   */
  private final AugmentableDatabaseChangeLog createAugmentableDatabaseChangeLog(final Database database, final String changeLogResourceName) throws LiquibaseException {
    final Liquibase throwaway = new Liquibase(changeLogResourceName, this.getResourceAccessor(), database);
    final AugmentableDatabaseChangeLog returnValue = new AugmentableDatabaseChangeLog();
    copyState(throwaway.getDatabaseChangeLog(), returnValue);
    return returnValue;
  }


  /*
   * Static methods.
   */


  public static final TestRule newInstance() {
    return newInstance((Iterable<? extends String>)null, new H2Rule());
  }

  public static final TestRule newInstance(final String changeLogResourceName, DataSource dataSource, final String... contexts) {
    return newInstance(changeLogResourceName == null ? (Iterable<? extends String>)null : Arrays.asList(changeLogResourceName), dataSource, contexts);
  }
  
  public static final TestRule newInstance(final Iterable<? extends String> changeLogResourceNames, DataSource dataSource, final String... contexts) {
    if (dataSource == null) {
      dataSource = new H2Rule();
    }
    final LiquiunitRule liquibase = new LiquiunitRule(dataSource, contexts);
    if (changeLogResourceNames != null) {
      liquibase.setChangeLogResourceNames(changeLogResourceNames);
    }    
    if (dataSource instanceof TestRule) {
      return RuleChain.outerRule((TestRule)dataSource).around(liquibase);
    } else {
      return liquibase;
    }
  }

  /**
   * A hackish method that would not be needed except that by default
   * the {@link DatabaseChangeLog#include(String, boolean,
   * ResourceAccessor)} method is not {@code public}.
   *
   * <p>This method copies the state from the {@code oldLog} parameter
   * into the {@code newLog} parameter.</p>
   *
   * <p>The properties that are copied are those that are present in
   * version 3.3.0 of the {@link DatabaseChangeLog} class.  Updates to
   * <a href="http://www.liquibase.org/">Liquibase</a> may alter the
   * state that needs to be copied; in such a case this method will
   * need to be changed.</p>
   *
   * @param oldLog the {@link DatabaseChangeLog} from which state
   * should be copied; may be {@code null} in which case no action
   * will be taken
   *
   * @param newLog the {@link DatabaseChangeLog} to which state should
   * be copied; may be {@code null} in which case no action will be
   * taken
   *
   * @see <a
   * href="https://liquibase.jira.com/browse/CORE-2125"><code>CORE-2125</code></a>
   */
  private static final void copyState(final DatabaseChangeLog oldLog, final DatabaseChangeLog newLog) {
    if (oldLog != null && newLog != null) {
      newLog.setChangeLogParameters(oldLog.getChangeLogParameters());
      newLog.setIgnoreClasspathPrefix(oldLog.ignoreClasspathPrefix());
      newLog.setLogicalFilePath(oldLog.getLogicalFilePath());
      newLog.setObjectQuotingStrategy(oldLog.getObjectQuotingStrategy());
      newLog.setPhysicalFilePath(oldLog.getPhysicalFilePath());
      newLog.setPreconditions(oldLog.getPreconditions());
      newLog.setRuntimeEnvironment(oldLog.getRuntimeEnvironment());
      final Collection<ChangeSet> changeSets = oldLog.getChangeSets();
      assert changeSets != null;
      for (final ChangeSet changeSet : changeSets) {
        newLog.addChangeSet(changeSet);
      }
    }
  }

  /*
   * Inner and nested classes.
   */


  private static final class AugmentableDatabaseChangeLog extends DatabaseChangeLog {

    private AugmentableDatabaseChangeLog() {
      super();
    }

    public final boolean include(final String filename, final ResourceAccessor resourceAccessor) throws LiquibaseException {
      return this.include(filename, false, resourceAccessor);
    }
    
  }
  
}
