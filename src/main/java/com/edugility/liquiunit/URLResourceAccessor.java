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

import java.io.InputStream;
import java.io.IOException;

import java.net.MalformedURLException;
import java.net.URL;

import liquibase.resource.ClassLoaderResourceAccessor;

/**
 * A {@link ClassLoaderResourceAccessor} that knows how to treat
 * resource requests as {@link String} representations of {@link
 * URL}s.
 *
 * @author <a href="http://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see #getResourceAsStream(String)
 *
 * @see URL#URL(String)
 *
 * @see URL#openStream()
 */
public class URLResourceAccessor extends ClassLoaderResourceAccessor {

  /**
   * Creates a new {@link URLResourceAccessor}.
   */
  public URLResourceAccessor() {
    this(Thread.currentThread().getContextClassLoader());
  }

  /**
   * Creates a new {@link URLResourceAccessor}.
   *
   * @param classLoader the {@link ClassLoader} to use in implementing
   * the {@link #getResources(String)} method; passed to the
   * {@linkplain
   * ClassLoaderResourceAccessor#ClassLoaderResourceAccessor(ClassLoader)
   * superclass constructor} as-is; must not be {@code null}
   */
  public URLResourceAccessor(final ClassLoader classLoader) {
    super(classLoader);
  }

  /**
   * Treats the supplied {@code name} as a {@link String}
   * representation of a {@link URL}, attempts to {@linkplain
   * URL#openStream() open a stream to it}, and returns the resulting
   * (open) {@link InputStream}.
   *
   * <p>This method may return {@code null}.</p>
   *
   * <p>If the supplied {@code name} parameter could not be accepted
   * by the {@link URL#URL(String)} constructor, then the return value
   * of {@link
   * ClassLoaderResourceAccessor#getResourceAsStream(String)} is
   * returned instead.</p>
   *
   * @param name a {@link String} representation of a URL; must not be
   * {@code null}
   *
   * @return an open {@link InputStream} or {@code null}
   *
   * @exception IOException if a problem was encountered {@linkplain
   * URL#openStream() opening a stream}
   *
   * @see ClassLoaderResourceAccessor#getResourceAsStream(String)
   */
  @Override
  public InputStream getResourceAsStream(final String name) throws IOException {
    InputStream returnValue = null;
    if (name != null) {
      try {
        returnValue = new URL(name).openStream();
      } catch (final MalformedURLException ignore) {
        returnValue = null;
      }
    }
    if (returnValue == null) {
      returnValue = super.getResourceAsStream(name);
    }
    return returnValue;
  }

}
