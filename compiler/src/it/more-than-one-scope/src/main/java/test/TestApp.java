/*
 * Copyright (C) 2013 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package test;

import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.inject.AbstractModule;

import com.google.inject.Provides;

import javax.inject.Scope;

import javax.inject.Singleton;

class TestApp {

  @Singleton
  @CustomScope
  public class TestClass1 {}

  /**
   * Class has three annotations, two of which are scope annotations.
   */
  @Singleton
  @SuppressWarnings("foo")
  @CustomScope
  public class TestClass2 {}

  /**
   * Module has @Provides method with two scoping annotations.
   */
  public class TestModule extends AbstractModule {

    @Singleton
    @CustomScope
    @Provides
    String providesString() {
      return "string";
    }

    @Override
    protected void configure() {}

  }

  @Scope
  @Retention(RUNTIME)
  @interface CustomScope {}
}
