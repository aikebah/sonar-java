/*
 * SonarQube Java
 * Copyright (C) 2012-2019 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.java.checks;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.check.Rule;
import org.sonar.check.RuleProperty;
import org.sonar.java.matcher.MethodMatcher;
import org.sonar.java.matcher.MethodMatcherCollection;
import org.sonar.java.matcher.NameCriteria;
import org.sonar.java.matcher.TypeCriteria;
import org.sonar.java.model.ModifiersUtils;
import org.sonar.plugins.java.api.JavaFileScanner;
import org.sonar.plugins.java.api.JavaFileScannerContext;
import org.sonar.plugins.java.api.semantic.Symbol;
import org.sonar.plugins.java.api.semantic.SymbolMetadata;
import org.sonar.plugins.java.api.tree.BaseTreeVisitor;
import org.sonar.plugins.java.api.tree.MethodInvocationTree;
import org.sonar.plugins.java.api.tree.MethodReferenceTree;
import org.sonar.plugins.java.api.tree.MethodTree;
import org.sonar.plugins.java.api.tree.Modifier;
import org.sonar.plugins.java.api.tree.NewClassTree;
import org.sonar.plugins.java.api.tree.Tree;

import static org.apache.commons.lang.StringUtils.isEmpty;

@Rule(key = "S2699")
public class AssertionsInTestsCheck extends BaseTreeVisitor implements JavaFileScanner {

  private static final Logger LOG = Loggers.get(AssertionsInTestsCheck.class);

  private static final String SHOULD = "should";
  private static final String VERIFY = "verify";
  private static final String ASSERT_NAME = "assert";

  private static final TypeCriteria ORG_ASSERTJ_ASSERTIONS = TypeCriteria.is("org.assertj.core.api.Assertions");
  private static final TypeCriteria ORG_ASSERTJ_FAIL = TypeCriteria.is("org.assertj.core.api.Fail");
  private static final TypeCriteria IO_RESTASSURED = TypeCriteria.is("io.restassured.response.ValidatableResponseOptions");

  private static final TypeCriteria ANY_TYPE = TypeCriteria.anyType();
  private static final NameCriteria ANY_NAME = NameCriteria.any();
  private static final NameCriteria STARTS_WITH_FAIL = NameCriteria.startsWith("fail");
  private static final NameCriteria STARTS_WITH_ASSERT = NameCriteria.startsWith(ASSERT_NAME);

  private static final MethodMatcherCollection ASSERTION_INVOCATION_MATCHERS = MethodMatcherCollection.create(
    // junit
    method("org.junit.Assert", STARTS_WITH_ASSERT).withAnyParameters(),
    method("org.junit.Assert", STARTS_WITH_FAIL).withAnyParameters(),
    method("org.junit.rules.ExpectedException", NameCriteria.startsWith("expect")).withAnyParameters(),
    method(TypeCriteria.subtypeOf("junit.framework.Assert"), STARTS_WITH_ASSERT).withAnyParameters(),
    method(TypeCriteria.subtypeOf("junit.framework.Assert"), STARTS_WITH_FAIL).withAnyParameters(),
    method("org.junit.rules.ErrorCollector", "checkThat").withAnyParameters(),
    // fest 1.x
    method(TypeCriteria.subtypeOf("org.fest.assertions.GenericAssert"), ANY_NAME).withAnyParameters(),
    method("org.fest.assertions.Assertions", STARTS_WITH_ASSERT).withAnyParameters(),
    method("org.fest.assertions.Fail", STARTS_WITH_FAIL).withAnyParameters(),
    // fest 2.x
    method(TypeCriteria.subtypeOf("org.fest.assertions.api.AbstractAssert"), ANY_NAME).withAnyParameters(),
    method("org.fest.assertions.api.Fail", STARTS_WITH_FAIL).withAnyParameters(),

    // rest assured 2.0
    method(IO_RESTASSURED, NameCriteria.is("body")).withAnyParameters(),
    method(IO_RESTASSURED, NameCriteria.is("time")).withAnyParameters(),
    method(IO_RESTASSURED, NameCriteria.startsWith("content")).withAnyParameters(),
    method(IO_RESTASSURED, NameCriteria.startsWith("status")).withAnyParameters(),
    method(IO_RESTASSURED, NameCriteria.startsWith("header")).withAnyParameters(),
    method(IO_RESTASSURED, NameCriteria.startsWith("cookie")).withAnyParameters(),
    method(IO_RESTASSURED, NameCriteria.startsWith("spec")).withAnyParameters(),

    // ReactiveX
    // 2.x
    method(TypeCriteria.subtypeOf("io.reactivex.observers.BaseTestConsumer"), STARTS_WITH_ASSERT).withAnyParameters(),
    // 1.x
    method(TypeCriteria.subtypeOf("rx.observers.TestObserver"), STARTS_WITH_ASSERT).withAnyParameters(),
    method(TypeCriteria.subtypeOf("rx.observers.TestSubscriber"), STARTS_WITH_ASSERT).withAnyParameters(),
    method(TypeCriteria.is("rx.observers.AssertableSubscriber"), STARTS_WITH_ASSERT).withAnyParameters(),

    // assertJ
    method(TypeCriteria.subtypeOf("org.assertj.core.api.AbstractAssert"), ANY_NAME).withAnyParameters(),
    method(ORG_ASSERTJ_FAIL, STARTS_WITH_FAIL).withAnyParameters(),
    method(ORG_ASSERTJ_FAIL, "shouldHaveThrown").withAnyParameters(),
    method(ORG_ASSERTJ_ASSERTIONS, STARTS_WITH_FAIL).withAnyParameters(),
    method(ORG_ASSERTJ_ASSERTIONS, "shouldHaveThrown").withAnyParameters(),
    method(ORG_ASSERTJ_ASSERTIONS, STARTS_WITH_ASSERT).withAnyParameters(),
    method(TypeCriteria.subtypeOf("org.assertj.core.api.AbstractSoftAssertions"), STARTS_WITH_ASSERT).withAnyParameters(),
    // hamcrest
    method("org.hamcrest.MatcherAssert", STARTS_WITH_ASSERT).withAnyParameters(),
    // Mockito
    method("org.mockito.Mockito", NameCriteria.startsWith(VERIFY)).withAnyParameters(),
    method("org.mockito.InOrder", NameCriteria.startsWith(VERIFY)).withAnyParameters(),
    // spring
    method("org.springframework.test.web.servlet.ResultActions", "andExpect").addParameter(ANY_TYPE),
    // EasyMock
    method("org.easymock.EasyMock", VERIFY).withAnyParameters(),
    method(TypeCriteria.subtypeOf("org.easymock.IMocksControl"), VERIFY).withAnyParameters(),
    method(TypeCriteria.subtypeOf("org.easymock.EasyMockSupport"), "verifyAll").withAnyParameters(),
    // Truth Framework
    method("com.google.common.truth.Truth", STARTS_WITH_ASSERT).withAnyParameters(),
    method("com.google.common.truth.Truth8", STARTS_WITH_ASSERT).withAnyParameters(),
    // JMock Mockery
    method(TypeCriteria.subtypeOf("org.jmock.Mockery"), "assertIsSatisfied").withAnyParameters(),
    // WireMock
    method("com.github.tomakehurst.wiremock.client.WireMock", VERIFY).withAnyParameters(),
    method("com.github.tomakehurst.wiremock.WireMockServer", VERIFY).withAnyParameters(),
    // Eclipse Vert.x
    method("io.vertx.ext.unit.TestContext", STARTS_WITH_ASSERT).withAnyParameters(),
    method("io.vertx.ext.unit.TestContext", STARTS_WITH_FAIL).withAnyParameters(),
    // Selenide
    method("com.codeborne.selenide.SelenideElement", NameCriteria.startsWith(SHOULD)).withAnyParameters(),
    method("com.codeborne.selenide.ElementsCollection", NameCriteria.startsWith(SHOULD)).withAnyParameters());

  private static final MethodMatcher JMOCKIT_CONSTRUCTOR_MATCHER = method("mockit.Verifications", "<init>").withAnyParameters();

  @RuleProperty(
    key = "customAssertionFrameworksMethods",
    description = "List of fully qualified method symbols that should be considered as assertion methods. The wildcard character can be used at the end of the method name.",
    defaultValue = "")
  public String customAssertionFrameworksMethods = "";
  private MethodMatcherCollection customAssertionMethodsMatcher = null;

  private final Deque<Boolean> methodContainsAssertion = new ArrayDeque<>();
  private final Deque<Boolean> inUnitTest = new ArrayDeque<>();
  private final Map<Symbol, Boolean> assertionInMethod = new HashMap<>();
  private JavaFileScannerContext context;

  @Override
  public void scanFile(final JavaFileScannerContext context) {
    this.context = context;
    assertionInMethod.clear();
    inUnitTest.push(false);
    methodContainsAssertion.push(false);
    scan(context.getTree());
    methodContainsAssertion.pop();
    inUnitTest.pop();
    assertionInMethod.clear();
  }

  @Override
  public void visitMethod(MethodTree methodTree) {
    if (ModifiersUtils.hasModifier(methodTree.modifiers(), Modifier.ABSTRACT)) {
      return;
    }
    boolean isUnitTest = isUnitTest(methodTree);
    inUnitTest.push(isUnitTest);
    methodContainsAssertion.push(false);
    super.visitMethod(methodTree);
    Boolean containsAssertion = methodContainsAssertion.pop();
    inUnitTest.pop();
    if (isUnitTest && !expectAssertion(methodTree) && !containsAssertion) {
      context.reportIssue(this, methodTree.simpleName(), "Add at least one assertion to this test case.");
    }
  }

  @Override
  public void visitMethodInvocation(MethodInvocationTree mit) {
    super.visitMethodInvocation(mit);
    if (shouldCheckForAssertion() && isAssertion(mit)) {
      methodContainsAssertion.pop();
      methodContainsAssertion.push(true);
    }
  }

  @Override
  public void visitMethodReference(MethodReferenceTree methodReferenceTree) {
    super.visitMethodReference(methodReferenceTree);
    if (shouldCheckForAssertion() && isAssertion(methodReferenceTree)) {
      methodContainsAssertion.pop();
      methodContainsAssertion.push(true);
    }
  }

  @Override
  public void visitNewClass(NewClassTree tree) {
    super.visitNewClass(tree);
    if (shouldCheckForAssertion() && JMOCKIT_CONSTRUCTOR_MATCHER.matches(tree)) {
      methodContainsAssertion.pop();
      methodContainsAssertion.push(true);
    }
  }

  private boolean shouldCheckForAssertion() {
    return !methodContainsAssertion.peek() && inUnitTest.peek();
  }

  private boolean isAssertion(MethodInvocationTree mit) {
    return ASSERTION_INVOCATION_MATCHERS.anyMatch(mit) || getCustomAssertionMethodsMatcher().anyMatch(mit) || isLocalMethodWithAssertion(mit.symbol());
  }

  private boolean isAssertion(MethodReferenceTree methodReferenceTree) {
    return ASSERTION_INVOCATION_MATCHERS.anyMatch(methodReferenceTree)
      || getCustomAssertionMethodsMatcher().anyMatch(methodReferenceTree)
      || isLocalMethodWithAssertion(methodReferenceTree.method().symbol());
  }

  private boolean isLocalMethodWithAssertion(Symbol symbol) {
    return assertionInMethod.computeIfAbsent(symbol, key -> {
      Tree declaration = key.declaration();
      if (declaration == null) {
        return false;
      } else {
        AssertionVisitor assertionVisitor = new AssertionVisitor(getCustomAssertionMethodsMatcher());
        declaration.accept(assertionVisitor);
        return assertionVisitor.hasAssertion;
      }
    });
  }

  private MethodMatcherCollection getCustomAssertionMethodsMatcher() {
    if (customAssertionMethodsMatcher == null) {
      String[] fullyQualifiedMethodSymbols = customAssertionFrameworksMethods.split(",");
      List<MethodMatcher> customMethodMatchers = new ArrayList<>(fullyQualifiedMethodSymbols.length);
      for (String fullyQualifiedMethodSymbol : fullyQualifiedMethodSymbols) {
        String[] methodMatcherParts = fullyQualifiedMethodSymbol.split("#");
        if (methodMatcherParts.length == 2 && !isEmpty(methodMatcherParts[0].trim()) && !isEmpty(methodMatcherParts[1].trim())) {
          String methodName = methodMatcherParts[1].trim();
          NameCriteria nameCriteria;
          if (methodName.endsWith("*")) {
            nameCriteria = NameCriteria.startsWith(methodName.substring(0, methodName.length() - 1));
          } else {
            nameCriteria = NameCriteria.is(methodName);
          }
          customMethodMatchers.add(method(methodMatcherParts[0].trim(), nameCriteria).withAnyParameters());
        } else {
          LOG.warn("Unable to create a corresponding matcher for custom assertion method, please check the format of the following symbol: '{}'", fullyQualifiedMethodSymbol);
        }
      }

      customAssertionMethodsMatcher = MethodMatcherCollection.create(customMethodMatchers.toArray(new MethodMatcher[0]));
    }

    return customAssertionMethodsMatcher;
  }

  private static boolean expectAssertion(MethodTree methodTree) {
    List<SymbolMetadata.AnnotationValue> annotationValues = methodTree.symbol().metadata().valuesForAnnotation("org.junit.Test");
    if (annotationValues != null) {
      for (SymbolMetadata.AnnotationValue annotationValue : annotationValues) {
        if ("expected".equals(annotationValue.name())) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isUnitTest(MethodTree methodTree) {
    Symbol.MethodSymbol symbol = methodTree.symbol();
    while (symbol != null) {
      if (symbol.metadata().isAnnotatedWith("org.junit.Test")) {
        return true;
      }
      symbol = symbol.overriddenSymbol();
    }
    Symbol.TypeSymbol enclosingClass = methodTree.symbol().enclosingClass();
    return enclosingClass != null && enclosingClass.type().isSubtypeOf("junit.framework.TestCase") && methodTree.simpleName().name().startsWith("test");
  }

  private static MethodMatcher method(String typeDefinition, String methodName) {
    return method(TypeCriteria.is(typeDefinition), NameCriteria.is(methodName));
  }

  private static MethodMatcher method(TypeCriteria typeDefinitionCriteria, String methodName) {
    return method(typeDefinitionCriteria, NameCriteria.is(methodName));
  }

  private static MethodMatcher method(String typeDefinition, NameCriteria nameCriteria) {
    return MethodMatcher.create().typeDefinition(TypeCriteria.is(typeDefinition)).name(nameCriteria);
  }

  private static MethodMatcher method(TypeCriteria typeDefinitionCriteria, NameCriteria nameCriteria) {
    return MethodMatcher.create().typeDefinition(typeDefinitionCriteria).name(nameCriteria);
  }

  private static class AssertionVisitor extends BaseTreeVisitor {
    boolean hasAssertion = false;
    private MethodMatcherCollection customAssertionMethodsMatcher;

    public AssertionVisitor(MethodMatcherCollection customAssertionMethodsMatcher) {
      this.customAssertionMethodsMatcher = customAssertionMethodsMatcher;
    }

    @Override
    public void visitMethodInvocation(MethodInvocationTree mit) {
      super.visitMethodInvocation(mit);
      if (!hasAssertion && (ASSERTION_INVOCATION_MATCHERS.anyMatch(mit) || customAssertionMethodsMatcher.anyMatch(mit))) {
        hasAssertion = true;
      }
    }

    @Override
    public void visitMethodReference(MethodReferenceTree methodReferenceTree) {
      super.visitMethodReference(methodReferenceTree);
      if (!hasAssertion && (ASSERTION_INVOCATION_MATCHERS.anyMatch(methodReferenceTree) || customAssertionMethodsMatcher.anyMatch(methodReferenceTree))) {
        hasAssertion = true;
      }
    }

    @Override
    public void visitNewClass(NewClassTree tree) {
      super.visitNewClass(tree);
      if (!hasAssertion && JMOCKIT_CONSTRUCTOR_MATCHER.matches(tree)) {
        hasAssertion = true;
      }
    }
  }

}
