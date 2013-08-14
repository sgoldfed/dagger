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

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import javax.inject.Inject;

import java.lang.annotation.Retention;

import com.google.inject.BindingAnnotation;

import javax.inject.Qualifier;

class TestApp {

  /**
   * Class has a javax qualifier annotation on the class itself and a non injectable constructor.
   */
  @JavaxQualifier
  public class TestClass1 {
    @JavaxQualifier public TestClass1() {}
  }
  
  /**
   * Class has a Guice Binding Annotation on the class itself and a non injectable constructor.
   */
  @GuiceBindingAnnotation
  public class TestClass2 {
    @GuiceBindingAnnotation public TestClass2() {}
  }
  
  /**
   * Class has a javax qualifier on an injectable constructor.
   */
  public class TestClass3 {
    @JavaxQualifier
    @Inject
    public TestClass3() {}
  }
  
  /**
   * Class has a Guice Binding Annotation on an injectable constructor.
   */
  public class TestClass4 {
    @GuiceBindingAnnotation
    @Inject
    public TestClass4() {}
  }
  
  @Qualifier
  @Retention(RUNTIME)
  public @interface JavaxQualifier {}
  
  @BindingAnnotation
  @Retention(RUNTIME)
  public @interface GuiceBindingAnnotation {}

}
