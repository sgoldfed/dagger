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

import com.google.inject.AbstractModule;
import com.google.inject.BindingAnnotation;
import com.google.inject.Provides;

import javax.inject.Qualifier;
import java.lang.annotation.Retention;

public class ProperUseOfQualifiers {
  /**
   * A class in with no annotations on any of its members.
   */
  public class TestClass1 {
    private int field;

    public TestClass1() {}

    public void method(int param) {}
  }

  /**
   * A class which has qualifiers on fields, injectable constructor parameters, 
   * and injected method parameters.
   */
  public class TestClass2 {
    
    @Inject 
    public TestClass2(@JavaxQualifier int param1, @GuiceBindingAnnotation String param2) {}
    
    @JavaxQualifier
    @Inject
    int field1;

    @GuiceBindingAnnotation
    @Inject
    int field2;
    
    @Inject
    void method(@JavaxQualifier String param1, @GuiceBindingAnnotation int param2) {}
  }
  
  /**
   * A Module with @Provides methods and @Provides method parameters that have a single qualifier
   */
  public class TestModule extends AbstractModule{
    
    @JavaxQualifier
    @Provides
    String providesString1(@JavaxQualifier int param) {
      return "string1";
    }

    @GuiceBindingAnnotation
    @Provides
    String providesString2(@GuiceBindingAnnotation int param) {
      return "string2";
    }
    
  @Override
  protected void configure() {}
  }
  
  @Qualifier
  @Retention(RUNTIME)
  public @interface JavaxQualifier {}
  
  @BindingAnnotation
  @Retention(RUNTIME)
  public @interface GuiceBindingAnnotation {}

}
