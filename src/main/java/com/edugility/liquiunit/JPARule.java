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

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;
import javax.persistence.PersistenceContext;

import org.junit.rules.ExternalResource;

import org.junit.runner.Description;

import org.junit.runners.model.Statement;

public class JPARule extends ExternalResource {

  private final Object testInstance;

  private List<Field> fields;

  private Description description;

  private EntityManager em;

  private EntityManagerFactory emf;

  private boolean closeFactory;

  private final String persistenceUnitName;

  private final Map<?, ?> entityManagerFactoryProperties;

  private final Map<?, ?> entityManagerProperties;

  private final H2Rule h2Rule;

  public JPARule(final Object testInstance, final String persistenceUnitName, final H2Rule h2Rule) {
    super();
    if (testInstance == null) {
      throw new IllegalArgumentException("testInstance", new NullPointerException("testInstance"));
    }
    this.testInstance = testInstance;
    this.persistenceUnitName = persistenceUnitName;
    this.h2Rule = h2Rule;
    this.entityManagerFactoryProperties = null;
    this.entityManagerProperties = null;
  }

  public JPARule(final Object testInstance, final String persistenceUnitName) {
    this(testInstance, Persistence.createEntityManagerFactory(persistenceUnitName), null);
  }

  public JPARule(final Object testInstance, final String persistenceUnitName, final Map<?, ?> entityManagerFactoryProperties, final Map<?, ?> entityManagerProperties) {
    super();
    if (testInstance == null) {
      throw new IllegalArgumentException("testInstance", new NullPointerException("testInstance"));
    }
    this.testInstance = testInstance;
    this.persistenceUnitName = persistenceUnitName;
    this.h2Rule = null;
    this.entityManagerFactoryProperties = entityManagerFactoryProperties;
    this.entityManagerProperties = entityManagerProperties;
  }

  public JPARule(final Object testInstance, final EntityManagerFactory emf) {
    this(testInstance, emf, null);
  }

  public JPARule(final Object testInstance, final EntityManagerFactory emf, final Map<?, ?> entityManagerProperties) {
    super();
    if (testInstance == null) {
      throw new IllegalArgumentException("testInstance", new NullPointerException("testInstance"));
    }
    this.testInstance = testInstance;
    this.persistenceUnitName = null;
    this.h2Rule = null;
    this.entityManagerFactoryProperties = emf == null ? null : emf.getProperties();
    this.entityManagerProperties = entityManagerProperties;
    this.emf = emf;
  }

  @Override
  public void before() throws IllegalAccessException, InvocationTargetException {
    if (this.emf == null || !this.emf.isOpen()) {
      this.closeFactory = true;
      final Map<?, ?> entityManagerFactoryProperties = this.getEntityManagerFactoryProperties();
      if (entityManagerFactoryProperties == null || entityManagerFactoryProperties.isEmpty()) {
        this.emf = Persistence.createEntityManagerFactory(this.persistenceUnitName);
      } else {
        this.emf = Persistence.createEntityManagerFactory(this.persistenceUnitName, entityManagerFactoryProperties);
      }
    }
    if (this.emf != null && this.emf.isOpen()) {
      final Thread currentThread = Thread.currentThread();
      assert currentThread != null;
      if (this.entityManagerProperties == null || this.entityManagerProperties.isEmpty()) {
        this.em = this.emf.createEntityManager();
      } else {
        this.em = this.emf.createEntityManager(this.entityManagerProperties);
      }

      if (this.description != null) {
        final Class<?> testClass = description.getTestClass();
        if (testClass != null) {
          final List<Field> fields = new ArrayList<Field>();
          Class<?> cls = testClass;
          while (cls != null) {
            final Field[] declaredFields = cls.getDeclaredFields();
            if (declaredFields != null && declaredFields.length > 0) {
              for (final Field f : declaredFields) {
                if (f != null && EntityManager.class.isAssignableFrom(f.getType())) {
                  final PersistenceContext pc = f.getAnnotation(PersistenceContext.class);
                  if (pc != null) {
                    final boolean accessible = f.isAccessible();
                    try {
                      if (testClass.equals(f.getDeclaringClass())) {
                        f.setAccessible(true);
                      }
                      f.set(this.testInstance, this.em);
                      fields.add(f);
                    } catch (final SecurityException ohWell) {
                      // ignore
                    } finally {
                      f.setAccessible(accessible);
                    }
                  }
                }
              }
            }
            cls = cls.getSuperclass();
          }
          this.fields = fields;
        }
      }
    }
  }

  public Map<?, ?> getEntityManagerFactoryProperties() {
    final Map<?, ?> returnValue;
    if (this.h2Rule != null) {
      returnValue = this.h2Rule.getJPAProperties();
    } else {
      returnValue = this.entityManagerFactoryProperties;
    }
    return returnValue;
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

  @Override
  public void after() {
    if (this.em != null && this.em.isOpen()) {
      final EntityTransaction et = this.em.getTransaction();
      if (et != null && et.isActive()) {
        et.rollback();
      }
      this.em.close();      
    }    
    this.em = null;
    if (this.fields != null && !this.fields.isEmpty()) {
      for (final Field f : this.fields) {
        if (f != null) {
          final boolean old = f.isAccessible();
          try {
            f.setAccessible(true);
            f.set(this.testInstance, null);
          } catch (final IllegalAccessException wrapMe) {
            throw new RuntimeException(wrapMe);
          } finally {
            f.setAccessible(old);
          }
        }
      }
      this.fields = null;
    }
    if (this.closeFactory && this.emf != null && this.emf.isOpen()) {
      this.emf.close();
      this.closeFactory = false;
    }
    this.description = null;
  }

  public void close() {
    if (this.em != null && this.em.isOpen()) {
      this.em.close();
      this.em = null;
    }
    if (this.emf != null) {
      this.emf.close();
    }
  }

  public EntityManagerFactory getEntityManagerFactory() {
    return this.emf;
  }

  public EntityManager getEntityManager() {
    return this.em;
  }

}
