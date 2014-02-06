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

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

public class TestCaseLiquiunitRule {

  @Rule
  public TestRule rule3;

  public TestCaseLiquiunitRule() {
    super();
    System.out.println("(test) TestCaseLiquiunitRule created; thread id: " + Thread.currentThread().getId() + "; pid: " + LiquiunitRule.pid() + "; fork number: " + System.getProperty("junk", "NONE"));
    System.out.println("(test) TestCaseLiquiunitRule: testDatabaseConnectionURL: " + System.getProperty("testDatabaseConnectionURL"));
    final TestRule db = new LiquiunitRule(null);
    final TestRule liquibase = new LiquiunitRule(null);
    this.rule3 = RuleChain.outerRule(db).around(liquibase);
  }

  @BeforeClass
  public static void beforeClass() {
    System.out.println("(test) TestCaseLiquiunitRule.beforeClass() invoked; thread id: " + Thread.currentThread().getId() + "; pid: " + LiquiunitRule.pid());
  }

  @Before
  public void before() {
    System.out.println("(test) TestCaseLiquiunitRule.before() invoked; thread id: " + Thread.currentThread().getId() + "; pid: " + LiquiunitRule.pid());
  }

  @Test
  public void testStub1() throws Exception {
    System.out.println("(test) TestCaseLiquiunitRule.testStub1() invoked; thread id: " + Thread.currentThread().getId() + "; pid: " + LiquiunitRule.pid());
    Thread.sleep(2000L);
  }

  @Test
  public void testStub2() throws Exception {
    System.out.println("(test) TestCaseLiquiunitRule.testStub2() invoked; thread id: " + Thread.currentThread().getId() + "; pid: " + LiquiunitRule.pid());
    Thread.sleep(2000L);
  }

  @After
  public void after() {
    System.out.println("(test) TestCaseLiquiunitRule.after() invoked; thread id: " + Thread.currentThread().getId() + "; pid: " + LiquiunitRule.pid());
  }

  @AfterClass
  public static void afterClass() {
    System.out.println("(test) TestCaseLiquiunitRule.afterClass() invoked; thread id: " + Thread.currentThread().getId() + "; pid: " + LiquiunitRule.pid());
  }

}
