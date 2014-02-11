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

import java.io.IOException;

import java.net.URL;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.dbunit.AbstractDatabaseTester;
import org.dbunit.DataSourceDatabaseTester;
import org.dbunit.DefaultOperationListener;
import org.dbunit.IDatabaseTester;

import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;

import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.dataset.IDataSet;

import org.dbunit.dataset.datatype.IDataTypeFactory;

import org.dbunit.dataset.xml.XmlDataSet;

import org.junit.rules.ExternalResource;

import org.junit.runner.Description;

import org.junit.runners.model.Statement;

import org.xml.sax.InputSource;

/**
 * An {@link ExternalResource} that wraps JUnit tests with a {@link
 * DataSourceDatabaseTester} to ensure that the underlying database is
 * populated appropriately with test data.
 *
 * @author <a href="http://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see <a href="http://www.dbunit.org/">dbUnit</a>
 *
 * @see DataSourceDatabaseTester
 */
public class DataSourceDatabaseTesterRule extends ExternalResource {
  

  /*
   * Instance fields.
   */


  /**
   * The underlying {@link DataSourceDatabaseTester} that forms the
   * basis of this {@link DataSourceDatabaseTesterRule}.
   *
   * <p>This field may be {@code null}.</p>
   */
  protected final DataSourceDatabaseTester tester;

  /**
   * The {@link Description} describing the current JUnit test
   * underway.
   *
   * <p>This field may be {@code null}.</p>
   */
  private Description description;

  /**
   * The {@link IDataSet} that may have previously been installed in
   * the affiliated {@link #tester DataSourceDatabaseTester}.
   *
   * <p>This field may be {@code null}.</p>
   */
  private IDataSet oldDataSet;


  /*
   * Constructors.
   */


  /**
   * Creates a {@link DataSourceDatabaseTesterRule}.
   *
   * @param dataSource the {@link DataSource} that will back a new
   * {@link DataSourceDatabaseTester} {@linkplain
   * DataSourceDatabaseTester#DataSourceDatabaseTester(DataSource)
   * created} by this constructor; must not be {@code null}
   *
   * @exception NullPointerException if {@code dataSource} is {@code
   * null}
   *
   * @see DataSourceDatabaseTester#DataSourceDatabaseTester(DataSource)
   */
  public DataSourceDatabaseTesterRule(final DataSource dataSource) {
    super();
    this.tester = new DataSourceDatabaseTester(dataSource);
  }

  /**
   * Creates a {@link DataSourceDatabaseTesterRule}.
   *
   * @param dataSource the {@link DataSource} that will back a new
   * {@link DataSourceDatabaseTester} {@linkplain
   * DataSourceDatabaseTester#DataSourceDatabaseTester(DataSource)
   * created} by this constructor; must not be {@code null}
   *
   * @param dataTypeFactory the {@link IDataTypeFactory} that
   * describes data types for the database to which the supplied
   * {@link DataSource} is notionally connected; may be {@code null}
   *
   * @exception NullPointerException if {@code dataSource} is {@code
   * null}
   *
   * @see DataSourceDatabaseTester#DataSourceDatabaseTester(DataSource)
   */
  public DataSourceDatabaseTesterRule(final DataSource dataSource, final IDataTypeFactory dataTypeFactory) {
    this(dataSource);
    this.installDataTypeFactory(dataTypeFactory);
  }

  /**
   * Creates a {@link DataSourceDatabaseTesterRule}.
   *
   * @param dataSource the {@link DataSource} that will back a new
   * {@link DataSourceDatabaseTester} {@linkplain
   * DataSourceDatabaseTester#DataSourceDatabaseTester(DataSource,
   * String) created} by this constructor; must not be {@code null}
   *
   * @param schema the parameter value to supply to the {@link
   * DataSourceDatabaseTester#DataSourceDatabaseTester(DataSource,
   * String)} constructor; may be {@code null}
   *
   * @see
   * DataSourceDatabaseTester#DataSourceDatabaseTester(DataSource,
   * String)
   */
  public DataSourceDatabaseTesterRule(final DataSource dataSource, final String schema) {
    super();
    this.tester = new DataSourceDatabaseTester(dataSource, schema);
  }

  /**
   * Creates a {@link DataSourceDatabaseTesterRule}.
   *
   * @param dataSource the {@link DataSource} that will back a new
   * {@link DataSourceDatabaseTester} {@linkplain
   * DataSourceDatabaseTester#DataSourceDatabaseTester(DataSource,
   * String) created} by this constructor; must not be {@code null}
   *
   * @param schema the parameter value to supply to the {@link
   * DataSourceDatabaseTester#DataSourceDatabaseTester(DataSource,
   * String)} constructor; may be {@code null}
   *
   * @param dataTypeFactory the {@link IDataTypeFactory} that
   * describes data types for the database to which the supplied
   * {@link DataSource} is notionally connected; may be {@code null}
   *
   * @see
   * DataSourceDatabaseTester#DataSourceDatabaseTester(DataSource,
   * String)
   */
  public DataSourceDatabaseTesterRule(final DataSource dataSource, final String schema, final IDataTypeFactory dataTypeFactory) {
    this(dataSource, schema);
    this.installDataTypeFactory(dataTypeFactory);
  }

  /**
   * Creates a {@link DataSourceDatabaseTesterRule}.
   *
   * <p>This constructor assumes that the supplied {@link
   * DataSourceDatabaseTester} is completely configured and this
   * {@link DataSourceDatabaseTesterRule} will therefore perform no
   * further configuration.  Please see in particular the {@link
   * #getDataSet(Description)} method, which will consequently have no
   * effect.</p>
   *
   * @param tester the {@link DataSourceDatabaseTester} to which most
   * operations will delegate; must not be {@code null}
   *
   * @exception IllegalArgumentException if {@code tester} is {@code
   * null}
   */
  public DataSourceDatabaseTesterRule(final DataSourceDatabaseTester tester) {
    super();
    if (tester == null) {
      throw new IllegalArgumentException("tester", new NullPointerException("tester"));
    }
    this.tester = tester;
  }


  /*
   * Instance methods.
   */


  /**
   * Ensures that every {@link IDatabaseConnection} produced during
   * the course of execution is configured to use the supplied {@link
   * IDataTypeFactory}.
   *
   * @param dataTypeFactory the {@link IDataTypeFactory} suitable for
   * the underlying database; may be {@code null}
   *
   * @see IOperationListener#connectionRetrieved(IDatabaseConnection)
   *
   * @see DatabaseConfig#PROPERTY_DATATYPE_FACTORY
   */
  private final void installDataTypeFactory(final IDataTypeFactory dataTypeFactory) {
    if (dataTypeFactory != null && this.tester != null) {
      this.tester.setOperationListener(new DefaultOperationListener() {
          @Override
          public final void connectionRetrieved(final IDatabaseConnection connection) {
            super.connectionRetrieved(connection);
            if (connection != null) {
              final DatabaseConfig config = connection.getConfig();
              if (config != null) {
                config.setProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY, dataTypeFactory);
              }
            }
          }
        });
    }
  }

  /**
   * {@linkplain #getDataSet(Description) Finds an appropriate
   * <code>IDataSet</code>} for the {@linkplain #tester affiliated
   * <code>DataSourceDatabaseTester</code>} and {@linkplain
   * AbstractDatabaseTester#setDataSet(IDataSet) installs it}
   * immediately before invoking the {@link IDatabaseTester#onSetup()}
   * method.
   *
   * @exception if an error occurs
   */
  @Override
  public void before() throws Exception {
    if (this.tester != null) {
      final IDataSet oldDataSet = this.tester.getDataSet();
      this.oldDataSet = oldDataSet;
      if (oldDataSet == null) {
        IDataSet newDataSet = this.getDataSet(this.description);
        if (newDataSet == null) {
          newDataSet = new DefaultDataSet();
        }
        this.tester.setDataSet(newDataSet);
      }
      this.tester.onSetup();
    }
  }

  /**
   * Invokes the {@link IDatabaseTester#onTearDown()} method.
   *
   * @see IDatabaseTester#onTearDown()
   */
  @Override
  public void after() {
    if (this.tester != null) {
      try {
        this.tester.onTearDown();
      } catch (final RuntimeException throwMe) {
        throw throwMe;
      } catch (final Exception everythingElse) {
        throw new RuntimeException(everythingElse); // TODO: ugly
      }
      this.tester.setDataSet(this.oldDataSet);
    }
    this.description = null; // XXX TODO INVESTIGATE: not sure this is proper
  }

  /**
   * Overrides the {@link ExternalResource#apply(Statement,
   * Description)} method to store the supplied {@link Description}
   * for usage by the {@link #before()} method internally and returns
   * the superclass' return value.
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
   * @see #before()
   *
   * @see ExternalResource#apply(Statement, Description)
   */
  @Override
  public Statement apply(final Statement base, final Description description) {
    this.description = description;
    return super.apply(base, description);
  }

  /**
   * Follows various conventions, detailed below, in attempting to
   * locate and instantiate a new {@link IDataSet} instance
   * appropriate for the supplied {@link Description}.
   *
   * <p>This implementation first checks the {@link #tester
   * DataSourceDatabaseTester} indirectly or directly supplied to this
   * {@link DataSourceDatabaseTesterRule} at {@linkplain
   * #DataSourceDatabaseTesterRule(DataSource) construction time} to
   * see if {@linkplain DataSourceDatabaseTester#getDataSet() it
   * already has a <code>IDataSet</code> implementation installed}.
   * If so, then no further action is taken and {@code null} is
   * returned.  {@code null} is also returned if the supplied {@code
   * description} is {@code null}.<p>
   *
   * <p>Otherwise, a {@linkplain ClassLoader#getResource(String)
   * classpath resource} named {@code
   * datasets/SIMPLE_TEST_CLASS_NAME/TEST_METHOD_NAME.xml} is sought
   * using the {@linkplain Thread#getContextClassLoader() context
   * classloader}, where {@code SIMPLE_TEST_CLASS_NAME} is the name of
   * the JUnit test {@link Class} currently running and {@code
   * TEST_METHOD_NAME} is the name of the JUnit test method currently
   * running.</p>
   *
   * <p>If that resource doesn't exist, then a {@linkplain
   * ClassLoader#getResource(String) classpath resource} named {@code
   * datasets/SIMPLE_TEST_CLASS_NAME.xml} is sought using the
   * {@linkplain Thread#getContextClassLoader() context classloader},
   * where {@code SIMPLE_TEST_CLASS_NAME} is the name of the JUnit
   * test {@link Class} currently running.</p>
   *
   * <p>Once a resource is located in this manner, a new {@link
   * XmlDataSet} is constructed from it and returned.</p>
   *
   * <p>If no resource exists, then {@code null} is returned.</p>
   *
   * @param description a {@link Description} describing the JUnit
   * test being executed; may be {@code null}
   *
   * @return an {@link IDataSet} instance appropriate for the supplied
   * {@link Description}, or {@code null}
   *
   * @exception DataSetException if there was an error in constructing
   * the {@link IDataSet}
   *
   * @exception IOException if there was an input/output error
   */
  protected IDataSet getDataSet(final Description description) throws DataSetException, IOException {
    IDataSet returnValue = null;
    if (this.tester != null) {
      final IDataSet old = this.tester.getDataSet();
      if (old == null && description != null) {
        final String simpleClassName;
        final Class<?> testClass = description.getTestClass();
        if (testClass == null) {
          simpleClassName = null;
        } else {
          simpleClassName = testClass.getSimpleName();
        }
        final String methodName = description.getMethodName();
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
          cl = ClassLoader.getSystemClassLoader();
          if (cl == null) {
            cl = this.getClass().getClassLoader();
          }
        }
        assert cl != null;
        URL url = cl.getResource(String.format("datasets/%s/%s.xml", simpleClassName, methodName));
        if (url == null) {
          url = cl.getResource(String.format("datasets/%s.xml", simpleClassName));
        }
        if (url != null) {
          returnValue = new XmlDataSet(url.openStream());
        }
      }
    }
    return returnValue;
  }

}
