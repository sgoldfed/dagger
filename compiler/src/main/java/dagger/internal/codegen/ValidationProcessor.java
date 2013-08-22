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
package dagger.internal.codegen;

import static javax.lang.model.element.ElementKind.CLASS;
import static javax.lang.model.element.ElementKind.CONSTRUCTOR;
import static javax.lang.model.element.ElementKind.METHOD;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;

import com.google.inject.BindingAnnotation;
import com.google.inject.Provides;
import com.google.inject.ScopeAnnotation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.inject.Qualifier;
import javax.inject.Scope;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

/**
 * Static checks for Guice. This processor validates that @Inject and scoping annotations are used
 * on the correct types of elements, that a class doesn't have multiple injectable constructors,
 * and that at most one qualifier or scoping annotation is used on an element. Also verified is
 * that @Provides methods are only declared in modules, that @AssistedInject and @Inject are not
 * mixed incorrectly, and that @Assisted parameter are properly disambiguated.
 *
 * <p> The implemented checks and suppression logic are summarized below:
 *
 *<table border="1">
 *  <tr>
 *    <th>Severity</th>
 *    <th>Summary</th>
 *    <th>Suppression Logic</th>
 *  </tr>
 *  <tr>
 *    <td>Error</td>
 *    <td>class has more than one injectable constructor</td>
 *    <td>{@code @SuppressWarnings("MoreThanOneInjectableConstructor")}
 *       <br>The suppression annotation is placed on the class
 *    </td>
 *  </tr>
 *  <tr>
 *    <td>Error</td>
 *    <td>@javax.inject.Inject is on a final field</td>
 *    <td>{@code @SuppressWarnings("JavaxInjectOnFinalField")}</td>
 *  </tr>
 *  <tr>
 *    <td>Warning</td>
 *    <td>@com.google.inject.Inject is on a final field</td>
 *    <td>{@code @SuppressWarnings("GuiceInjectOnFinalField")}</td>
 *  </tr>
 *  <tr>
 *    <td>Error</td>
 *    <td>@javax.inject.Inject is on an abstract method</td>
 *    <td>{@code @SuppressWarnings("JavaxInjectOnAbstractMethod")}</td>
 *  </tr>
 *  <tr>
 *    <td>Error</td>
 *    <td>@AssistedInject and @Inject are on the same constructor</td>
 *    <td>{@code @SuppressWarnings("AssistedInjectAndInjectOnSameConstructor")} </td>
 *  </tr>
 *  <tr>
 *    <td>Warning</td>
 *    <td>@Inject and @AssistedInject are on different constructors in the same class</td>
 *    <td>{@code @SuppressWarnings("AssistedInjectAndInjectOnConstructors")}
 *         <br>The suppression annotation is placed on the class
 *    </td>
 *  </tr>
 *  <tr>
 *    <td>Error</td>
 *    <td>constructor has @Assisted parameters that have the same
 *      type and same value(or no value) for the @Assisted annotation.
 *    </td>
 *    <td>{@code @SuppressWarnings("AmbiguousAssistedParameters")} </td>
 *  </tr>
 *  <tr>
 *    <td>Error</td>
 *    <td>@com.google.inject.Provides is used outside of a Guice or Gin Module</td>
 *    <td>{@code @SuppressWarnings("GuiceProvidesNotInModule")} </td>
 *  </tr>
 *  <tr>
 *    <td>Error</td>
 *    <td>element is annotated with more than one qualifier annotation</td>
 *    <td>{@code @SuppressWarnings("MoreThanOneQualifier")} </td>
 *  </tr>
 *  <tr>
 *    <td>Error</td>
 *    <td>class or method is annotated with more than one scoping annotation</td>
 *    <td>{@code @SuppressWarnings("MoreThanOneScopeAnnotation")} </td>
 *  </tr>
 *  <tr>
 *    <td>Error</td>
 *    <td>scoping annotation is on an interface or abstract class</td>
 *    <td>{@code @SuppressWarnings("ScopeAnnotationOnInterfaceOrAbstractClass")} </td>
 *  </tr>
 *  <tr>
 *    <td>Error</td>
 *    <td>scoping annotation is anywhere other than a method or class</td>
 *    <td>{@code @SuppressWarnings("ScopingAnnotationNotOnMethodOrClass")} </td>
 *  </tr>
 *</table>
 *
 * @author sgoldfeder@google.com (Steven Goldfeder)
 */
@SupportedAnnotationTypes({ "*" })
public final class ValidationProcessor extends AbstractProcessor {

  private static final String ASSISTED_INJECT_ANNOTATION =
      "com.google.inject.assistedinject.AssistedInject";


  private static final String ASSISTED_ANNOTATION = "com.google.inject.assistedinject.Assisted";

  // suppression strings
  private static final String MORE_THAN_ONE_INJECTABLE_CONSTRUCTOR =
      "MoreThanOneInjectableConstructor";
  private static final String JAVAX_INJECT_ON_FINAL_FIELD = "JavaxInjectOnFinalField";
  private static final String GUICE_INJECT_ON_FINAL_FIELD = "GuiceInjectOnFinalField";
  private static final String JAVAX_INJECT_ON_ABSTRACT_METHOD = "JavaxInjectOnAbstractMethod";
  private static final String ASSISTED_INJECT_AND_INJECT_ON_SAME_CONSTRUCTOR =
      "AssistedInjectAndInjectOnSameConstructor";
  private static final String ASSISTED_INJECT_AND_INJECT_ON_CONSTRUCTORS =
      "AssistedInjectAndInjectOnConstructors";
  private static final String AMBIGUOUS_ASSISTED_PARAMETERS = "AmbiguousAssistedParameters";
  private static final String GUICE_PROVIDES_NOT_IN_MODULE = "GuiceProvidesNotInModule";
  private static final String MORE_THAN_ONE_QUALIFIER = "MoreThanOneQualifier";
  private static final String MORE_THAN_ONE_SCOPE_ANNOTATION = "MoreThanOneScopeAnnotation";
  private static final String SCOPE_ANNOTATION_ON_INTERFACE_OR_ABSTRACT_CLASS =
      "ScopeAnnotationOnInterfaceOrAbstractClass";
  private static final String SCOPING_ANNOTATION_NOT_ON_METHOD_OR_CLASS =
      "ScopingAnnotationNotOnMethodOrClass";

  @Override public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public boolean process(Set<? extends TypeElement> types, RoundEnvironment env) {
    List<Element> allElements = new ArrayList<Element>();
    Map<Element, Element> parametersToTheirMethods = new LinkedHashMap<Element, Element>();
    getAllElements(env, allElements, parametersToTheirMethods);
    for (Element element : allElements) {
      validateInjectables(element);
      validateAssistedParameters(element);
      validateProvides(element);
      validateScoping(element);
      validateQualifiers(element);
    }
    return false;
  }

  /**
   * Checks for @Inject on an abstract method, or a final field. Annotating a final field with
   * {@code @javax.inject.Inject} is an error, and with @com.google.inject.Inject is a warning.
   * This method also calls {@code validateInjectableConstructors}.
   *
   */
  private void validateInjectables(Element element) {
   validateInjectableConstructors(element);
    if (!hasInjectAnnotation(element)) {
      return;
    }
    switch (element.getKind()) {
      case METHOD:
        if (element.getModifiers().contains(ABSTRACT) && hasJavaxInject(element)
            && !isSuppressed(element, JAVAX_INJECT_ON_ABSTRACT_METHOD)) {
          error("@javax.inject.Inject cannot be placed on an abstract method: "
              + elementToString(element), element);
        }
        break;
      case FIELD:
        if (element.getModifiers().contains(FINAL) && hasJavaxInject(element)
            && !isSuppressed(element, JAVAX_INJECT_ON_FINAL_FIELD)) {
          error("A final field cannot be annotated with @javax.inject.Inject: "
              + elementToString(element), element);
        }
        if (element.getModifiers().contains(FINAL) && hasComGoogleInject(element)
            && !isSuppressed(element, GUICE_INJECT_ON_FINAL_FIELD)) {
          warning("Injecting a final field with @com.google.inject.Inject is discouraged: "
              + elementToString(element), element);
        }
        break;
        default:
    }
  }

  /**
   * Checks for mixing @Inject and @AssistedInject on constructors in the same class. If they are
   * on different constructors, this is a warning as it's legal at runtime but makes the code
   * confusing. If @Inject and @AssistedInject are on the same constructor, this is an error. This
   * method also gives an error if a class has more than one injectable constructor.
   */
  private void validateInjectableConstructors(Element element) {
    if (element.getKind() != CLASS) {
      return;
    }
    int numberOfInjectableConstructors = 0;
    boolean hasAssistedInjectConstructor = false;
    boolean gaveErrorForMixingOnSameConstructor = false;
    for (Element enclosed : element.getEnclosedElements()) {
      if (enclosed.getKind().equals(CONSTRUCTOR) && hasInjectAnnotation(enclosed)
          && hasAssistedInjectAnnotation(enclosed)
          && !isSuppressed(enclosed, ASSISTED_INJECT_AND_INJECT_ON_SAME_CONSTRUCTOR)) {
        error("A constructor cannot be annotated with both @Inject and @AssistedInject: "
            + elementToString(enclosed), enclosed);
        gaveErrorForMixingOnSameConstructor = true;
      }
      if (enclosed.getKind().equals(CONSTRUCTOR) && hasInjectAnnotation(enclosed)) {
        numberOfInjectableConstructors++;
      }
      if (enclosed.getKind().equals(CONSTRUCTOR) && hasAssistedInjectAnnotation(enclosed)) {
        hasAssistedInjectConstructor = true;
      }
    }
    if (numberOfInjectableConstructors > 1
        && !isSuppressed(element, MORE_THAN_ONE_INJECTABLE_CONSTRUCTOR)) {
      error("Class has more than one injectable constructor: " + elementToString(element), element);
    }
    if (numberOfInjectableConstructors > 0 && hasAssistedInjectConstructor
        && !gaveErrorForMixingOnSameConstructor
        && !isSuppressed(element, ASSISTED_INJECT_AND_INJECT_ON_CONSTRUCTORS)) {
      warning("Class has both an @Inject constructor and an @AssistedInject constructor. "
          + "This leads to confusing code.\n"
          + "To suppress this warning, annotate the class with "
          + "@SuppressWarnings(\"" + ASSISTED_INJECT_AND_INJECT_ON_CONSTRUCTORS + "\"): "
          + elementToString(element), element);
    }
  }

  /**
   * Checks that @Assited paramaters are properly disambiguated. That is, all @Assisted parameters
   * of a constructor must either be of different types, different generic types, or be
   * disambiguated with named @Assisted annotations.
   */
  private void validateAssistedParameters(Element element) {
    if (element.getKind() != CONSTRUCTOR
        || (!hasInjectAnnotation(element) && !hasAssistedInjectAnnotation(element))) {
      return;
    }
    ExecutableElement injectableConstructor = (ExecutableElement) element;
    for (VariableElement parameter : injectableConstructor.getParameters()) {
      AnnotationMirror thisParamsAssisted = getAnnotation(parameter, ASSISTED_ANNOTATION);
      if (thisParamsAssisted != null) {
        int numIdentical = 0;
        for (VariableElement otherParameter : injectableConstructor.getParameters()) {
          AnnotationMirror otherParamsAssisted = getAnnotation(otherParameter, ASSISTED_ANNOTATION);
          if (isSameType(parameter, otherParameter) && otherParamsAssisted != null
              && haveIdenticalValues(thisParamsAssisted, otherParamsAssisted)) {
            numIdentical++;
          }
        }
        // 1 is expected since when we iterated through the parameters, we
        // compared it to every parameter including itself.
        if (numIdentical > 1 && !isSuppressed(element, AMBIGUOUS_ASSISTED_PARAMETERS)) {
          error("@Assisted parameters must not be the same type unless the annotations have "
              + "different values (e.g @Assisted(\"fg\") Color fg, @Assisted(\"bg\") Color bg: "
              + elementToString(parameter), parameter);
        }
      }
    }
  }

  /**
   * Checks that @com.google.inject.Provides methods are in classes that implement(directly
   * or indirectly) Guice's {@code Module} or @code{GinModule}.
   */
  private void validateProvides(Element element) {
    if (element.getKind().equals(METHOD) && element.getAnnotation(Provides.class) != null
        && !isGuiceOrGinModule(element)
        && !isSuppressed(element, GUICE_PROVIDES_NOT_IN_MODULE)) {
      error("@Provides methods must be declared in modules: " + elementToString(element), element);
    }
  }

  /**
   * The verbosity of this method is so that we don't have to depend on Gin. The Guice
   * parts could have been simplified, but I chose uniformity as this makes the  code
   * easier to understand and makes it clear when we are doing parallel operations for
   * Gin/Guice.
   */
  private boolean isGuiceOrGinModule(Element element) {
    TypeMirror guiceModule = null;
    TypeMirror ginModule = null;
    TypeElement guiceModuleElement =
        processingEnv.getElementUtils().getTypeElement("com.google.inject.Module");
    TypeElement ginModuleElement = processingEnv.getElementUtils()
        .getTypeElement("com.google.gwt.inject.client.GinModule");
    if (guiceModuleElement != null) {
      guiceModule = guiceModuleElement.asType();
    }
    if (ginModuleElement != null) {
      ginModule = ginModuleElement.asType();
    }
    boolean isGuiceModule = false;
    boolean isGinModule = false;
    if (guiceModule != null) {
      isGuiceModule = processingEnv.getTypeUtils()
          .isSubtype(element.getEnclosingElement().asType(), guiceModule);
    }
    if (ginModule != null) {
      isGinModule =
          processingEnv.getTypeUtils().isSubtype(element.getEnclosingElement().asType(), ginModule);
    }
    return isGuiceModule || isGinModule;
  }

  private void validateQualifiers(Element element) {
    int numberOfQualifiersOnElement = 0;
    for (AnnotationMirror annotation : element.getAnnotationMirrors()) {
      if (!isQualifierAnnotation(annotation)) {
        continue;
      }
      switch (element.getKind()) {
        case FIELD:
          numberOfQualifiersOnElement++;
          break;
        case METHOD:
          numberOfQualifiersOnElement++;
          break;
        case PARAMETER:
          numberOfQualifiersOnElement++;
          break;
        default:
      }
    }
    if (numberOfQualifiersOnElement > 1 && !isSuppressed(element, MORE_THAN_ONE_QUALIFIER)) {
      error("Only one qualifier annotation is allowed per element: " + elementToString(element),
          element);
    }
  }

  private void validateScoping(Element element) {
    int numberOfScopingAnnotationsOnElement = 0;
    for (AnnotationMirror annotation : element.getAnnotationMirrors()) {
      if (!isScopingAnnotation(annotation)) {
        continue;
      }
      switch (element.getKind()) {
        case METHOD:
          numberOfScopingAnnotationsOnElement++;
          break;
        case CLASS:
          if (element.getModifiers().contains(ABSTRACT)) {
            if (!isSuppressed(element, SCOPE_ANNOTATION_ON_INTERFACE_OR_ABSTRACT_CLASS)) {
              error("Scoping annotations are not allowed on abstract classes: "
                  + elementToString(element), element);
            }
          } else {
            numberOfScopingAnnotationsOnElement++;
          }
          break;
        case INTERFACE: // only valid in java 7 and above
          if (!isSuppressed(element, SCOPE_ANNOTATION_ON_INTERFACE_OR_ABSTRACT_CLASS)) {
            error("Scoping annotations are not allowed on interfaces: "
                + elementToString(element), element);
          }
          break;
        default:
          if (!isSuppressed(element, SCOPING_ANNOTATION_NOT_ON_METHOD_OR_CLASS))
          error("Scoping annotations are only allowed on concrete types and methods: "
              + elementToString(element), element);
      }
    }
    if (numberOfScopingAnnotationsOnElement > 1
        && !isSuppressed(element, MORE_THAN_ONE_SCOPE_ANNOTATION)) {
      error("Only one scoping annotation is allowed per element: " + elementToString(element),
          element);
    }
  }

  private void getAllElements(
      RoundEnvironment env, List<Element> result, Map<Element, Element> parametersToTheirMethods) {
    for (Element element : env.getRootElements()) {
      addAllEnclosed(element, result, parametersToTheirMethods);
    }
  }

  private void addAllEnclosed(
      Element element, List<Element> result, Map<Element, Element> parametersToTheirMethods) {
    result.add(element);
    for (Element enclosed : element.getEnclosedElements()) {
      addAllEnclosed(enclosed, result, parametersToTheirMethods);
      if (enclosed.getKind() == METHOD || enclosed.getKind() == CONSTRUCTOR) {
        for (Element parameter : ((ExecutableElement) enclosed).getParameters()) {
          result.add(parameter);
          parametersToTheirMethods.put(parameter, enclosed);
        }
      }
    }
  }

  private boolean hasInjectAnnotation(Element element) {
    return element.getAnnotation(javax.inject.Inject.class) != null
        || element.getAnnotation(com.google.inject.Inject.class) != null;
  }

  private boolean hasJavaxInject(Element element) {
    return element.getAnnotation(javax.inject.Inject.class) != null;
  }

  private boolean hasComGoogleInject(Element element) {
    return element.getAnnotation(com.google.inject.Inject.class) != null;
  }

  private boolean hasAssistedInjectAnnotation(Element element) {
    for (AnnotationMirror annotation : element.getAnnotationMirrors()) {
      if (annotation.getAnnotationType().toString().equals(ASSISTED_INJECT_ANNOTATION)) {
        return true;
      }
    }
    return false;
  }

  private boolean isScopingAnnotation(AnnotationMirror annotation) {
    return annotation.getAnnotationType().asElement().getAnnotation(Scope.class) != null
        || annotation.getAnnotationType().asElement().getAnnotation(ScopeAnnotation.class) != null;
  }

  private boolean isQualifierAnnotation(AnnotationMirror annotation) {
    return annotation.getAnnotationType().asElement().getAnnotation(Qualifier.class) != null
        || annotation.getAnnotationType().asElement().getAnnotation(BindingAnnotation.class)
        != null;
  }

  private boolean isSameType(Element a, Element b) {
    return processingEnv.getTypeUtils().isSameType(a.asType(), b.asType());
  }

  // TODO(sgoldfeder): better format for other types of elements?
 static String elementToString(Element element) {
   switch (element.getKind()) {
     case FIELD:
     // fall through
     case CONSTRUCTOR:
     // fall through
     case METHOD:
       return element.getEnclosingElement() + "." + element;
     default:
       return element.toString();
   }
 }

 private boolean haveIdenticalValues(AnnotationMirror annotation1, AnnotationMirror annotation2) {
   if (annotation1.getElementValues().isEmpty() && annotation2.getElementValues().isEmpty()) {
     return true;
   }
   if (annotation1.getElementValues().isEmpty() || annotation2.getElementValues().isEmpty()) {
     return false; // we know they're not both empty. Only one is empty so not identical
   }
   for (ExecutableElement e : annotation1.getElementValues().keySet()) {
     if (!(annotation2.getElementValues().get(e).getValue()
         .equals(annotation1.getElementValues().get(e).getValue()))) {
       return false;
     }
   }

   for (ExecutableElement e : annotation2.getElementValues().keySet()) {
     if (!(annotation1.getElementValues().get(e).getValue()
         .equals(annotation2.getElementValues().get(e).getValue()))) {
       return false;
     }
   }
   return true;
 }

  /**
   * Checks if the given element is annotated with the given annotation. Returns the
   * {@code AnnotationMirror} if it is and {@code null} otherwise
   *
   * @param annotation the fully qualified name of the annotation
   * @return returns the {@code AnnotationMirror} if the annotation is present and {@code null} if
   *         it is not present
   */
  private AnnotationMirror getAnnotation(Element element, String annotation) {
    for (AnnotationMirror annotationMirror : element.getAnnotationMirrors()) {
      if (annotationMirror.getAnnotationType().toString().equals(annotation)) {
        return annotationMirror;
      }
    }
    return null;
  }

  private boolean isSuppressed(Element element, String suppressionString) {
    return element.getAnnotation(SuppressWarnings.class) != null && Arrays.asList(
        element.getAnnotation(SuppressWarnings.class).value()).contains(suppressionString);
  }

  private void error(String msg, Element element) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, element);
  }

  private void warning(String msg, Element element) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, msg, element);
  }
}