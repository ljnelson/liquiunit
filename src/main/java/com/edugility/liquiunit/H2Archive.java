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
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.junit.runner.Description;

/**
 * A special-purpose class that saves an in-memory <a
 * href="http://www.h2database.com/">H2</a> database as a {@link
 * String} <a
 * href="http://www.h2database.com/html/grammar.html?#script">containing
 * all the DML and DDL to reconstitute it</a>.
 *
 * <p>This class is designed for use only in light integration-testing
 * scenarios.</p>
 *
 * <p>This class is safe for use by multiple threads.</p>
 *
 * @author <a href="http://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see H2Rule
 *
 * @see <a href="http://www.h2database.com/html/grammar.html?#script">the <code>SCRIPT</code> command</a>
 */
public class H2Archive {

  /**
   * A {@link ReadWriteLock} used to protect access to the data stored
   * as an archive of a given H2 database.
   *
   * <p>This field is never {@code null}.</p>
   */
  protected final ReadWriteLock dataLock;

  /**
   * The data that comprises the H2 archive.
   *
   * <p>This field may be {@code null} at any point.</p>
   *
   * <p>Access to this field must be mediated by the {@link #dataLock}
   * field.</p>
   *
   * @see #dataLock
   */
  private volatile String data;

  /**
   * Creates a new {@link H2Archive}.
   */
  public H2Archive() {
    super();
    this.dataLock = new ReentrantReadWriteLock();
  }

  /**
   * Provided that no archive data currently exists, atomically saves
   * the state of the H2 database reachable via the supplied {@link
   * Connection} as the newline-separated output of the H2 {@code
   * SCRIPT} command.
   *
   * <p>If this method has been called before, then a subsequent
   * invocation will not overwrite any existing data.</p>
   *
   * @param description the {@link Description} describing the current
   * JUnit test underway; ignored by this implementation but may be
   * useful for subclasses; may be {@code null}
   *
   * @param c a {@link Connection} to an in-memory H2 database; must
   * not be {@code null}; must be {@linkplain Connection#isValid(int)
   * valid}
   *
   * @return {@code true} if a save actually occurred; {@code false}
   * if no action was taken
   *
   * @exception IllegalArgumentException if {@code c} is {@code null}
   *
   * @exception IllegalStateException if the supplied {@link
   * Connection} is not {@linkplain Connection#isValid(int) valid}
   *
   * @exception SQLException if a database error occurs
   *
   * @see #loadUnlessEmpty(Description, Connection)
   */
  public boolean saveIfEmpty(final Description description, final Connection c) throws SQLException {
    if (c == null) {
      throw new IllegalArgumentException("c", new NullPointerException("c"));
    } else if (!c.isValid(0)) {
      throw new IllegalStateException("this.getConnection().isValid(0)");
    }
    boolean returnValue = false;
    
    try {
      this.dataLock.writeLock().lock();
      if (this.isEmpty(description)) {
        
        final StringBuilder sb = new StringBuilder();
        
        final Statement statement = c.createStatement();
        assert statement != null;
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
        
        this.data = sb.toString();
        returnValue = !this.isEmpty(description);
      }
    } finally {
      this.dataLock.writeLock().unlock();
    }
    return returnValue;
  }

  /**
   * Uses any stored DML or DDL commands this {@link H2Archive}
   * encapsulates (saved earlier by the {@link
   * #saveIfEmpty(Description, Connection)} method) to populate the
   * in-memory H2 database represented and attached to by the supplied
   * {@link Connection}.
   *
   * @param description the {@link Description} describing the current
   * JUnit test underway; ignored by this implementation but may be
   * useful for subclasses; may be {@code null}
   *
   * @param c a {@link Connection} to an in-memory H2 database; must
   * not be {@code null}; must be {@linkplain Connection#isValid(int)
   * valid}
   *
   * @return {@code true} if the state of the database was altered;
   * {@code false} otherwise
   * 
   * @exception IllegalArgumentException if {@code c} is {@code null}
   *
   * @exception IllegalStateException if the supplied {@link
   * Connection} is not {@linkplain Connection#isValid(int) valid}
   *
   * @exception SQLException if a database error occurs
   *
   * @see #saveIfEmpty(Description, Connection)
   */
  public boolean loadUnlessEmpty(final Description description, final Connection c) throws SQLException {
    if (c == null) {
      throw new IllegalStateException("this.getConnection()", new NullPointerException("this.getConnection()"));
    } else if (!c.isValid(0)) {
      throw new IllegalStateException("this.getConnection().isValid(0)");
    }
    boolean returnValue = false;
    try {
      this.dataLock.readLock().lock();
      if (!this.isEmpty(description)) {
        final Statement statement = c.createStatement();
        assert statement != null;
        try {
          statement.execute(this.data);
        } finally {
          try {
            statement.close();
          } catch (final SQLException neverMind) {
            // ignore on purpose
          }
        }
        returnValue = true;
      }
    } finally {
      this.dataLock.readLock().unlock();
    }
    return returnValue;
  }

  /**
   * Returns {@code true} if, for the supplied {@link Description},
   * this {@link H2Archive} is conceptually
   * "empty"&mdash;devoid of archive data that could be
   * {@linkplain #loadUnlessEmpty(Description, Connection) loaded}.
   *
   * @param description the {@link Description} describing the current
   * JUnit test underway; ignored by this implementation but may be
   * useful for subclasses; may be {@code null}
   *
   * @return {@code true} if this {@link H2Archive} is conceptually
   * empty with regards to the supplied {@link Description}; {@code
   * false} otherwise
   *
   * @see #loadUnlessEmpty(Description, Connection)
   */
  public boolean isEmpty(final Description description) {
    try {
      this.dataLock.readLock().lock();
      return this.data == null || this.data.isEmpty();
    } finally {
      this.dataLock.readLock().unlock();
    }
  }

}
