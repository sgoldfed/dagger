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

import com.google.inject.ScopeAnnotation;

import javax.inject.Scope;

import com.google.inject.Provides;

import com.google.inject.AbstractModule;

import javax.inject.Inject;

class TestApp {

  /**
   * Class has a javax scoping annotation on a field.
   */
  public class TestClass1 {
    @MyJavaxScopingAnnotation
    private String field;
  }

  /**
   * Class has a Guice scoping annotation on a field.
   */
  public class TestClass2 {
    @MyGuiceScopingAnnotation
    private String field;
  }

  /**
   * Class has a javax scoping annotation on a constructor, constructor parameter
   * and non @Provides method.
   */
  public class TestClass3 {
    
    @MyJavaxScopingAnnotation
    public TestClass3(@MyJavaxScopingAnnotation int constructorParam1) {}
  }
  
  /**
   * Class has a Guice scoping annotation on a constructor, constructor parameter
   * and non @Provides method.
   */
  public class TestClass4 {
    
    @MyGuiceScopingAnnotation
    public TestClass4(@MyGuiceScopingAnnotation int constructorParam2) {}
  }

  /**
   * Module has a javax scoping annotation on a parameter. The method is a @Provides
   * method, but this is not relevant and nevertheless an error.
   */
  public class TestModule1 extends AbstractModule {
    @Provides
    String provideString(@MyJavaxScopingAnnotation String methodParam1) {
      return string;
    }
  }
  
  /**
   * Module has a Guice scoping annotation on a parameter. The method is a @Provides
   * method, but this is not relevant and nevertheless an error.
   */
  public class TestModule2 extends AbstractModule {
    @Provides
    String provideString(@MyGuiceScopingAnnotation String methodParam2) {
      return string;
    }
  }
  
  
  @Scope
  public @interface MyJavaxScopingAnnotation {}
  
  @ScopeAnnotation
  public @interface MyGuiceScopingAnnotation {}

}
