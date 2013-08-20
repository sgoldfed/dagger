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

import com.google.inject.assistedinject.AssistedInject;

import com.google.inject.assistedinject.Assisted;

import java.util.List;

import javax.inject.Inject;

class TestApp {

  /**
   * Class has constructor with two @Assisted parameters of the same type.
   */
  public class TestClass1 {
    @Inject
    public TestClass1(int n, @Assisted String x, @Assisted String y, int z) {}
  }

  /**
   * Class has constructor with two @Assisted parameters of the same type and same value.
   */
  public class TestClass2 {
    @Inject
    public TestClass2(int n, @Assisted("foo") int x, @Assisted("foo") int y, String z) {}
  }

  /**
   * Class has  constructor with two @Assisted parameters of the same parameterized type.
   */
  public class TestClass3 {
    @Inject
    public TestClass3(
        int n, @Assisted("foo") List<String> x, @Assisted("foo") List<String> y, String z) {}
  }
  /**
   * Class has @AssistedInject constructors with ambiguous @Assisted parameters.
   */
  public class TestClass4 {
    @AssistedInject
    public TestClass4(
        int n, @Assisted("foo") List<String> x, @Assisted("foo") List<String> y, String z) {}
  
    @AssistedInject
    public TestClass4(int n, @Assisted("foo") int x, @Assisted("foo") int y, String z) {}

    @AssistedInject
    public TestClass4(int n, @Assisted String x, @Assisted String y, int z) {}
  }
}
