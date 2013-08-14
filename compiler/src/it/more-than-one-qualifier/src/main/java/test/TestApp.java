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

import com.google.inject.Provides;

import com.google.inject.AbstractModule;

import com.google.inject.BindingAnnotation;
import java.lang.annotation.Retention;
import javax.inject.Qualifier;

class TestApp {
  
  /**
   * A class in which the fields are annotated with two qualifiers.
   */
  public class TestClass1 {
    //two javax qualifiers
    @Foo1
    @Foo2
    private int field1;
    
    // two Guice Binding Annotations
    @Bar1
    @Bar2
    private int field2;
    
    // one javax qualifier and one Guice Binding Annotation
    @Foo1
    @Bar1
    private int field3;
  }

  /**
   * A module in which @Provides methods and @Provides method parameters have two
   * qualifier annotations.
   */
  
  public class TestModule extends AbstractModule {
    
    // two javax qualifiers
    @Foo1
    @Foo2
    @Provides
    public String providesString1(@Foo1 @Foo2 int param1) {
      return "string";
    }
    
    // two Guice Binding Annotations
    @Bar1
    @Bar2
    @Provides
    public String providesString2(@Bar1 @Bar2 int param2) {
      return "string";
    }
    
    // one javax qualifier and one Guice Binding Annotation
    @Foo1
    @Bar1
    @Provides
    public String providesString3(@Foo1 @Bar1 int param3) {
      return "string";
    }  
    
    @Override
    protected void configure() {}
  }

  @Qualifier
  @Retention(RUNTIME)
  public @interface Foo1 {
  }

  @Qualifier
  @Retention(RUNTIME)
  public @interface Foo2 {
  }

  @BindingAnnotation
  @Retention(RUNTIME)
  public @interface Bar1 {
  }

  @BindingAnnotation
  @Retention(RUNTIME)
  public @interface Bar2 {
  }

}
