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

import javax.inject.Singleton;

import com.google.inject.Provides;

import com.google.inject.AbstractModule;

public class ProperUseOfScopingAnnotations {
  
  /**
   * Class has no scoping annotation.
   */
  public class TestClass1 {
    public TestClass1(int n) {}
  }
  
  /**
   * Class has a single non scoping annotation. 
   */
  @SuppressWarnings("foo")
  public class TestClass2 {}

  /**
   * Has a scoping annotation on the class.
   */
  @Singleton
  public class TestClass3 {}
  
  /**
   * Class has two annotations, one of which is a scoping annotation.
   */
  @Singleton @SuppressWarnings("foo")
  public class TestClass4 {}
  

  /**
   * Module has scoping annotation on a @Provides method.
   */
  public class TestModule extends AbstractModule {

    @Provides
    @Singleton
    String providesString() {
      return "string";
    }
    
    @Override
    protected void configure() {}
  }

}
