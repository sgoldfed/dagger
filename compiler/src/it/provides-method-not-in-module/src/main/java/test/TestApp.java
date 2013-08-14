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

import java.io.Serializable;

import com.google.inject.Provides;

class TestApp {
  
  class NotAModule {
    @Provides String providesString(){
      return "string";
    }
  }
  
  /** Test when the class is a subclass/implements an interface */
  class AlsoNotAModule extends NotAModule implements Serializable {
    @Provides int providesInt(){
      return 5;
    }
  }
}
