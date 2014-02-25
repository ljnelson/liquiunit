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
import java.util.Collection;
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

/**
 * An {@link ExternalResource} that sets up an {@link EntityManager}
 * before each JUnit test run.
 *
 * <p>Clients may choose whether a single {@link EntityManagerFactory}
 * is used to create all {@link EntityManager}s, or whether an {@link
 * EntityManagerFactory} is created and {@linkplain
 * EntityManagerFactory#close() closed} before and after each test
 * run.</p>
 *
 * <p>Although creating an {@link EntityManagerFactory} can be a
 * heavyweight operation, it offers much more isolation than the
 * alternative, and the intensity of its resource usage can be offset
 * by running tests in parallel.</p>
 *
 * @author <a href="http://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see EntityManagerFactory
 *
 * @see ExternalResource
 *
 * @see Persistence#createEntityManagerFactory(String)
 */
public class JPARule extends ExternalResource {

  /**
   * The instance of the test that this {@link JPARule} is decorating.
   *
   * <p>This field may be {@code null}.</p>
   */
  private final Object testInstance;

  /**
   * A {@link Collection} of {@link Field}s that house {@link
   * EntityManager}s; used by the {@link #before()} method for
   * implementing injection.
   *
   * <p>This field may be {@code null}.</p>
   *
   * @see #before()
   */
  private Collection<Field> fields;

  /**
   * A {@link Description} describing the current test.
   *
   * <p>This field may be {@code null}.</p>
   */
  private Description description;

  /**
   * The {@link EntityManager} in effect for the current test.
   *
   * <p>This field may be {@code null}.</p>
   */
  private EntityManager em;

  /**
   * The {@link EntityManagerFactory} in effect for the current test.
   *
   * <p>This field may be {@code null}.</p>
   */
  private EntityManagerFactory emf;

  /**
   * Whether or not the {@link EntityManagerFactory} should be
   * {@linkplain EntityManagerFactory#close() closed} in the {@link
   * #after()} method.  Typically this field is set to {@code true}
   * when {@linkplain #JPARule(Object, EntityManagerFactory) an
   * <code>EntityManagerFactory</code> is passed in at construction
   * time}.
   *
   * @see #after()
   *
   * @see #JPARule(Object, EntityManagerFactory)
   */
  private boolean closeFactory;

  /**
   * The name of the persistence unit for which an {@link
   * EntityManagerFactory} should be created.
   *
   * <p>This field may be {@code null}.</p>
   */
  private final String persistenceUnitName;

  /**
   * A {@link Map} representing properties to be used during
   * {@linkplain Persistence#createEntityManagerFactory(String, Map)
   * <code>EntityManagerFactory</code> creation}.
   *
   * <p>This field may be {@code null}.</p>
   */
  private final Map<?, ?> entityManagerFactoryProperties;

  /**
   * A {@link Map} representing properties to be used during
   * {@linkplain EntityManagerFactory#createEntityManager(Map)
   * <code>EntityManager</code> creation}.
   *
   * <p>This field may be {@code null}.</p>
   */
  private final Map<?, ?> entityManagerProperties;

  /**
   * A {@link H2Rule} that might have been passed in at {@linkplain
   * #JPARule(Object, String, H2Rule) construction time}.  This {@link
   * H2Rule} will be used in that case to {@linkplain
   * H2Rule#getJPAProperties() produce properties} for a new {@link
   * EntityManagerFactory}.
   *
   * <p>This field may be {@code null}.</p>
   *
   * @see #JPARule(Object, String, H2Rule)
   *
   * @see H2Rule#getJPAProperties()
   */
  private final H2Rule h2Rule;


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link JPARule}.
   *
   * @param testInstance an instance of the test class; must not be
   * {@code null}
   *
   * @param persistenceUnitName the name of the persistence unit to
   * look for; must not be {@code null}
   *
   * @param h2Rule an {@link H2Rule} whose {@link
   * H2Rule#getJPAProperties()} method will provide the connection and
   * authentication information; must not be {@code null}
   *
   * @exception IllegalArgumentException if {@code testInstance} or
   * {@code persistenceUnitName} or {@code h2Rule} is {@code null}
   */
  public JPARule(final Object testInstance, final String persistenceUnitName, final H2Rule h2Rule) {
    super();
    if (testInstance == null) {
      throw new IllegalArgumentException("testInstance", new NullPointerException("testInstance"));
    } else if (persistenceUnitName == null) {
      throw new IllegalArgumentException("persistenceUnitName", new NullPointerException("persistenceUnitName"));
    } else if (h2Rule == null) {
      throw new IllegalArgumentException("h2Rule", new NullPointerException("h2Rule"));
    }
    this.testInstance = testInstance;
    this.persistenceUnitName = persistenceUnitName;
    this.h2Rule = h2Rule;
    this.entityManagerFactoryProperties = null;
    this.entityManagerProperties = null;
  }

  /**
   * Creates a new {@link JPARule}.
   *
   * <p>This constructor calls the {@link #JPARule(Object,
   * EntityManagerFactory, Map)} constructor supplying it with the
   * value of the {@code testInstance} parameter, the return value
   * that results from invoking the {@link
   * Persistence#createEntityManagerFactory(String)} method with the
   * supplied {@code persistenceUnitName} parameter and {@code
   * null}.</p>
   * 
   * @param testInstance an instance of the test class; must not be
   * {@code null}
   *
   * @param persistenceUnitName the name of the persistence unit to
   * look for; must not be {@code null}
   *
   * @exception IllegalArgumentException if {@code testInstance} or
   * {@code persistenceUnitName} is {@code null}
   *
   * @exception NullPointerException if {@code persistenceUnitName} is
   * {@code null}
   *
   * @see #JPARule(Object, EntityManagerFactory, Map)
   */
  public JPARule(final Object testInstance, final String persistenceUnitName) {
    this(testInstance, Persistence.createEntityManagerFactory(persistenceUnitName), null);
  }

  /**
   * Creates a new {@link JPARule}.
   *
   * @param testInstance an instance of the test class; must not be
   * {@code null}
   *
   * @param persistenceUnitName the name of the persistence unit to
   * look for; must not be {@code null}
   *
   * @param entityManagerFactoryProperties a {@link Map} of properties
   * to use when {@linkplain
   * Persistence#createEntityManagerFactory(String, Map) creating a
   * new <code>EntityManagerFactory</code>}; may be {@code null}
   *
   * @param entityManagerProperties a {@link Map} of properties to use
   * when {@linkplain EntityManagerFactory#createEntityManager(Map)
   * creating a new <code>EntityManager</code>}; may be {@code null}
   *
   * @exception IllegalArgumentException if {@code testInstance} or
   * {@code persistenceUnitName} is {@code null}
   */
  public JPARule(final Object testInstance, final String persistenceUnitName, final Map<?, ?> entityManagerFactoryProperties, final Map<?, ?> entityManagerProperties) {
    super();
    if (testInstance == null) {
      throw new IllegalArgumentException("testInstance", new NullPointerException("testInstance"));
    } else if (persistenceUnitName == null) {
      throw new IllegalArgumentException("persistenceUnitName", new NullPointerException("persistenceUnitName"));
    }
    this.testInstance = testInstance;
    this.persistenceUnitName = persistenceUnitName;
    this.h2Rule = null;
    this.entityManagerFactoryProperties = entityManagerFactoryProperties;
    this.entityManagerProperties = entityManagerProperties;
  }

  /**
   * Creates a new {@link JPARule}.
   *
   * <p>This constructor calls the {@link #JPARule(Object,
   * EntityManagerFactory, Map)} constructor, supplying {@code null}
   * for the third parameter.</p>
   *
   * @param testInstance an instance of the test class; must not be
   * {@code null}
   *
   * @param emf an {@link EntityManagerFactory}; may be {@code null}
   *
   * @exception IllegalArgumentException if {@code testInstance} is
   * {@code null}
   *
   * @see #JPARule(Object, EntityManagerFactory, Map)
   */
  public JPARule(final Object testInstance, final EntityManagerFactory emf) {
    this(testInstance, emf, null);
  }

  /**
   * Creates a new {@link JPARule}.
   *
   * @param testInstance an instance of the test class; must not be
   * {@code null}
   *
   * @param emf the {@link EntityManagerFactory} to use; may be {@code
   * null}
   *
   * @param entityManagerProperties the {@link Map} of properties to
   * use when {@linkplain
   * EntityManagerFactory#createEntityManager(Map) creating a new
   * <code>EntityManager</code>}; may be {@code null}
   *
   * @exception IllegalArgumentException if {@code testInstance} is
   * {@code null}
   */
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


  /*
   * Instance methods.
   */


  /**
   * Creates an {@link EntityManager} and injects it into all fields
   * in the test instance that are of type {@code EntityManager} and
   * that are annotated with {@link
   * PersistenceContext @PersistenceContext}.
   *
   * @exception IllegalAccessException if there was a reflection
   * problem
   *
   * @exception InvocationTargetException if there was a reflection
   * problem
   */
  @Override
  public void before() throws IllegalAccessException, InvocationTargetException {

    // If there's no EntityManagerFactory yet, or a pre-existing one,
    // acquire a new one for this test.
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

      // If for some crazy reason there's an open EntityManager
      // already installed, close it.
      if (this.em != null && this.em.isOpen()) {
        this.em.close();
      }

      // Create a new EntityManager.
      if (this.entityManagerProperties == null || this.entityManagerProperties.isEmpty()) {
        this.em = this.emf.createEntityManager();
      } else {
        this.em = this.emf.createEntityManager(this.entityManagerProperties);
      }

      if (this.description != null) {
        final Class<?> testClass = description.getTestClass();
        if (testClass != null) {

          // Look for all @PersistenceContext-annotated fields in the
          // test class and its superclasses.  Set the just-created
          // EntityManager into all of them on the current test
          // instance.  Keep track of which fields were set so they
          // can be cleared in the after() method.

          final Collection<Field> fields = new ArrayList<Field>();
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

  /**
   * Returns a {@link Map} that will be passed to the {@link
   * Persistence#createEntityManagerFactory(String, Map)} method to
   * create a new {@link EntityManagerFactory}.
   *
   * <p>Note that if an {@link H2Rule} has been passed to the
   * {@linkplain #JPARule(Object, String, H2Rule) relevant
   * constructor}, then the {@link Map} returned will be the return
   * value of the {@link H2Rule#getJPAProperties()} method.</p>
   *
   * <p>This method may return {@code null}.</p>
   *
   * @return a {@link Map} of {@link EntityManagerFactory} properties,
   * or {@code null}
   *
   * @see Persistence#createEntityManagerFactory(String, Map)
   * 
   * @see H2Rule#getJPAProperties()
   */
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

  /**
   * {@linkplain EntityTransaction#rollback() Rolls back} any
   * {@linkplain EntityTransaction#isActive() active} {@link
   * EntityTransaction}, {@linkplain EntityManager#close() closes} any
   * {@linkplain EntityManager#isOpen() open} {@link EntityManager},
   * sets all fields that were injected by the {@link #before()}
   * method to {@code null}, and closes any {@link
   * EntityManagerFactory} that was created by this {@link JPARule}.
   *
   * @see #before()
   */
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

  /**
   * Forcibly closes any open {@link EntityManager}s and {@link
   * EntityManagerFactory} instances, thus releasing resources.
   *
   * <p>Most callers do not need to invoke this method.</p>
   */
  public void close() {
    if (this.em != null) {
      this.em.close();
      this.em = null;
    }
    if (this.emf != null) {
      this.emf.close();
      this.emf = null;
    }
  }

  /**
   * Returns the {@link EntityManagerFactory} in effect.
   *
   * <p>This method may return {@code null}.</p>
   *
   * @return the {@link EntityManagerFactory} in effect, or {@code
   * null}
   */
  public EntityManagerFactory getEntityManagerFactory() {
    return this.emf;
  }

  /**
   * Returns the {@link EntityManager} in effect.
   *
   * <p>This method may return {@code null}.</p>
   *
   * @return the {@link EntityManager} in effect, or {@code null}
   */
  public EntityManager getEntityManager() {
    return this.em;
  }

}
