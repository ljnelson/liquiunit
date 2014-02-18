/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright (c) 2013 Edugility LLC.
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

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.dbunit.ext.h2.H2DataTypeFactory;

import org.junit.Rule;
import org.junit.Test;

import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

public class TestCaseJPARule {

  private static final H2Archive archive = new H2Archive();

  @Rule
  public final TestRule rule;

  @PersistenceContext
  private EntityManager em;

  public TestCaseJPARule() {
    super();
    final H2Rule h2 = new H2Rule(archive);
    final LiquiunitRule liquibase = new LiquiunitRule(h2);
    final DataSourceDatabaseTesterRule dbUnit = new DataSourceDatabaseTesterRule(h2, new H2DataTypeFactory());
    final TestRule jpaRule = new JPARule(this, "test", h2);
    this.rule = RuleChain.outerRule(h2).around(liquibase).around(dbUnit).around(jpaRule);
  }

  @Test
  public void test1() {
    System.out.println("Test JPA 1");
    System.out.println("jpaRule's entity manager (injected): " + this.em);
  }

  @Test
  public void test2() {
    System.out.println("Test JPA 2");
  }

}
