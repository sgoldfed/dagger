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

import javax.lang.model.element.AnnotationValue;

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
import javax.inject.Inject;
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
 * Static checks for Guice. This processor validates that @Inject, qualifier annotations, and
 * scoping annotations are used on the correct types of elements, that a class doesn't have multiple
 * injectable constructors, and that at most one qualifier or scoping annotation is used on an
 * element. Also verified is that @Provides methods are only declared in modules, that
 * @AssistedInject and @Inject are not mixed incorrectly, and that @Assisted parameters are properly
 * disambiguated.
 *
 * <p> Warnings for invalid use of qualifier annotations can be suppressed with
 * @SuppressWarnings("qualifiers")
 *
 * <p> Warnings for invalid use of scoping annotations can be suppressed with
 * @SuppressWarnings("scoping")
 *
 * <p> Warnings for injecting a final field with @com.google.inject.Inject and for using @Inject and
 * {@code @AssistedInject} on different constructors in the same class can be suppressed with
 * {@code @SuppressWarnings("inject")}
 *
 * @author sgoldfeder@google.com (Steven Goldfeder)
 */
@SupportedAnnotationTypes({ "*" })
public final class ValidationProcessor extends AbstractProcessor {

  private static final String ASSISTED_INJECT_ANNOTATION =
      "com.google.inject.assistedinject.AssistedInject";

  private static final String ASSISTED_ANNOTATION = "com.google.inject.assistedinject.Assisted";

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
      validateQualifiers(element, parametersToTheirMethods);
    }
    return false;
  }

  private void validateInjectables(Element element) {
    boolean suppressWarnings = element.getAnnotation(SuppressWarnings.class) != null
        && Arrays.asList(element.getAnnotation(SuppressWarnings.class).value()).contains("inject");
    validateInjectableConstructors(element, suppressWarnings);
    if (!hasInjectAnnotation(element)) {
      return;
    }
    switch (element.getKind()) {
      case METHOD:
        if (element.getModifiers().contains(ABSTRACT) && hasJavaxInject(element)) {
          error("@javax.inject.Inject cannot be placed on an abstract method: "
              + elementToString(element), element);
        }
        break;
      case FIELD:
        if (element.getModifiers().contains(FINAL) && hasJavaxInject(element)) {
          error("A final field cannot be annotated with @javax.inject.Inject: "
              + elementToString(element), element);
        }
        if (element.getModifiers().contains(FINAL) && hasComGoogleInject(element)
            && !suppressWarnings) {
          warning("Injecting a final field with @com.google.inject.Inject is discouraged: "
              + elementToString(element), element);
        }
        break;
        default:
    }
  }

  private void validateInjectableConstructors(Element element, boolean suppressWarnings) {
    if (element.getKind() != CLASS) {
      return;
    }
    int numberOfInjectableConstructors = 0;
    boolean hasAssistedInjectConstructor = false;
    for (Element enclosed : element.getEnclosedElements()) {
      if (enclosed.getKind().equals(CONSTRUCTOR) && hasInjectAnnotation(enclosed)
          && hasAssistedInjectAnnotation(enclosed)) {
        error("A constructor cannot be annotated with both @Inject and @AssistedInject: "
            + elementToString(enclosed), enclosed);
      }
      if (enclosed.getKind().equals(CONSTRUCTOR) && hasInjectAnnotation(enclosed)) {
        numberOfInjectableConstructors++;
      }
      if (enclosed.getKind().equals(CONSTRUCTOR) && hasAssistedInjectAnnotation(enclosed)) {
        hasAssistedInjectConstructor = true;
      }
    }
    if (numberOfInjectableConstructors > 1) {
      error("Class has more than one injectable constructor: " + elementToString(element), element);
    }
    // will it be weird to get this error as well if they're on the same constructor?
    if (numberOfInjectableConstructors > 0 && hasAssistedInjectConstructor && !suppressWarnings) {
      warning("Class has both an @Inject constructor and an @AssistedInject constructor. "
          + "This leads to confusing code: " + elementToString(element), element);
    }
  }

  private void validateAssistedParameters(Element element) {
    if (element.getKind() != CONSTRUCTOR || (!hasInjectAnnotation(element)
        && !hasAssistedInjectAnnotation(element))) {
      return;
    }
    ExecutableElement injectableConstructor = (ExecutableElement) element;
    for (VariableElement parameter : injectableConstructor.getParameters()) {
      AnnotationMirror assistedAnnotation = null;
      for (AnnotationMirror annotation : parameter.getAnnotationMirrors()) {
        if (annotation.getAnnotationType().toString().equals(ASSISTED_ANNOTATION)) {
          assistedAnnotation = annotation;
        }
      }
      if (assistedAnnotation != null) {
        int numIdentical = 0;
        for (VariableElement otherParameter : injectableConstructor.getParameters()) {
          if (processingEnv.getTypeUtils()
              .isSameType(parameter.asType(), otherParameter.asType())) {
            AnnotationMirror otherParamsAssisted = null;
            for (AnnotationMirror annotation : otherParameter.getAnnotationMirrors()) {
              if (annotation.getAnnotationType().toString().equals(ASSISTED_ANNOTATION)) {
                otherParamsAssisted = annotation;
              }
            }
            if (otherParamsAssisted != null) {
              Map<? extends ExecutableElement, ? extends AnnotationValue> thisParamsValues = assistedAnnotation.getElementValues();
              Map<? extends ExecutableElement, ? extends AnnotationValue> otherParamsValues = otherParamsAssisted.getElementValues();
              if(thisParamsValues.isEmpty() && otherParamsValues.isEmpty()){
                numIdentical++;
              }
              for (MethodSymbol m : thisParamsAssisted.getElementValues().keySet())
                if (otherParamsAssisted.getElementValues().get(m).getValue()
                    .equals(thisParamsAssisted.getElementValues().get(m).getValue())) {
                  numIdentical++;
                }
            }
          }
        }
        if (numIdentical > 1) { // 1 is expected since when we iterated through the parameters, we
                                // compared it to every parameter including itself.
          error("@Assisted parameters must not be the same type unless the annotations have "
                 + "different values (e.g @Assisted(\"fg\") Color fg, @Assisted(\"bg\") Color bg: "
                 + elementToString(injectableConstructor), injectableConstructor);
        }
      }
    }
  }

  private void validateProvides(Element element) {
    if (element.getKind().equals(METHOD) && element.getAnnotation(Provides.class) != null
        && !isGuiceModule(element.getEnclosingElement().asType())) {
      error("@Provides methods must be declared in modules: " + elementToString(element), element);
    }
  }

  private boolean isGuiceModule(TypeMirror type) {
    TypeMirror module =
        processingEnv.getElementUtils().getTypeElement("com.google.inject.Module").asType();
    return processingEnv.getTypeUtils().isSubtype(type, module);
  }

  private void validateQualifiers(Element element, Map<Element, Element> parametersToTheirMethods) {
    boolean suppressWarnings =
        element.getAnnotation(SuppressWarnings.class) != null && Arrays.asList(
            element.getAnnotation(SuppressWarnings.class).value()).contains("qualifiers");
    int numberOfQualifiersOnElement = 0;
    for (AnnotationMirror annotation : element.getAnnotationMirrors()) {
      if (!isQualifierAnnotation(annotation)) {
        continue;
      }
      switch (element.getKind()) {
        case FIELD:
          numberOfQualifiersOnElement++;
          if (element.getAnnotation(Inject.class) == null && !suppressWarnings) {
            warning("Guice will ignore qualifier annotations on fields that are not "
                + "annotated with @Inject: " + elementToString(element), element);
          }
          break;
        case METHOD:
          numberOfQualifiersOnElement++;
          break;
        case PARAMETER:
          numberOfQualifiersOnElement++;
          if (!isInjectableConstructorParameter(element, parametersToTheirMethods)
              && !isInjectableMethodParameter(element, parametersToTheirMethods)
              && !isProvidesMethodParameter(element, parametersToTheirMethods)
              && !suppressWarnings) {
            warning("Guice will ignore qualifier annotations on parameters that are not @Inject "
                + "constructor parameters, @Inject method parameters, or @Provides method "
                + "parameters: " + elementToString(element), element);
          }
          break;
        default:
          error("Qualifier annotations are only allowed on fields, methods, and parameters: "
              + elementToString(element), element);
      }
    }
    if (numberOfQualifiersOnElement > 1) {
      error("Only one qualifier annotation is allowed per element: " + elementToString(element),
          element);
    }
  }

  private void validateScoping(Element element) {
    boolean suppressWarnings =
        element.getAnnotation(SuppressWarnings.class) != null && Arrays.asList(
            element.getAnnotation(SuppressWarnings.class).value()).contains("scoping");
    int numberOfScopingAnnotationsOnElement = 0;
    for (AnnotationMirror annotation : element.getAnnotationMirrors()) {
      if (!isScopingAnnotation(annotation)) {
        continue;
      }
      switch (element.getKind()) {
        case METHOD:
          numberOfScopingAnnotationsOnElement++;
          if (!isProvidesMethod(element) && !suppressWarnings) {
            warning("Guice will ignore scoping annotations on methods that are not "
                + "@Provides methods: " + elementToString(element), element);
          }
          break;
        case CLASS:
          if (!element.getModifiers().contains(ABSTRACT)) { // includes interfaces
            numberOfScopingAnnotationsOnElement++;
            break;
          }
        // fall through if abstract
        default:
          error("Scoping annotations are only allowed on concrete types and @Provides methods: "
              + elementToString(element), element);
      }
    }
    if (numberOfScopingAnnotationsOnElement > 1) {
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

  /**
   * @param parameter an {@code Element} whose {@code Kind} is parameter. The {@code Kind} is not
   *        tested here.
   */
  private boolean isInjectableConstructorParameter(
      Element parameter, Map<Element, Element> parametersToTheirMethods) {
    return parametersToTheirMethods.get(parameter).getKind() == CONSTRUCTOR
        && hasInjectAnnotation(parametersToTheirMethods.get(parameter));
  }

  /**
   * @param parameter an {@code Element} whose {@code Kind} is parameter. The {@code Kind} is not
   *        tested here.
   */
  private boolean isInjectableMethodParameter(
      Element parameter, Map<Element, Element> parametersToTheirMethods) {
    return parametersToTheirMethods.get(parameter).getKind() == METHOD //test that cxtor excluded
        && hasInjectAnnotation(parametersToTheirMethods.get(parameter));
  }

  private boolean isProvidesMethod(Element element) {
    return element.getKind() == METHOD && element.getAnnotation(Provides.class) != null;
  }

  /**
   * @param parameter an {@code Element} whose {@code Kind} is parameter. The {@code Kind} is not
   *        tested here.
   */
  private boolean isProvidesMethodParameter(
      Element parameter, Map<Element, Element> parametersToTheirMethods) {
    return parametersToTheirMethods.get(parameter).getAnnotation(Provides.class) != null;
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

  private void error(String msg, Element element) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, element);
  }

  private void warning(String msg, Element element) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, msg, element);
  }
}