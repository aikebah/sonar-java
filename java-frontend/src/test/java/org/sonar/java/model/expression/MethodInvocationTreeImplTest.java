/*
 * SonarQube Java
 * Copyright (C) 2012-2025 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonar.java.model.expression;

import org.junit.jupiter.api.Test;
import org.sonar.java.model.JParserTestUtils;
import org.sonar.plugins.java.api.semantic.Symbol;
import org.sonar.plugins.java.api.tree.ClassTree;
import org.sonar.plugins.java.api.tree.CompilationUnitTree;
import org.sonar.plugins.java.api.tree.ExpressionStatementTree;
import org.sonar.plugins.java.api.tree.MethodInvocationTree;
import org.sonar.plugins.java.api.tree.MethodTree;
import org.sonar.plugins.java.api.tree.StatementTree;
import org.sonar.plugins.java.api.tree.SyntaxToken;

import static org.assertj.core.api.Assertions.assertThat;

class MethodInvocationTreeImplTest {

  @Test
  void symbol_should_be_set() {
    CompilationUnitTree cut = createTree("class A { void foo(){} void bar(){foo();} }");
    ClassTree classTree = (ClassTree) cut.types().get(0);
    Symbol.MethodSymbol declaration = ((MethodTree) classTree.members().get(0)).symbol();
    StatementTree statementTree = ((MethodTree) classTree.members().get(1)).block().body().get(0);
    MethodInvocationTree mit = (MethodInvocationTree) ((ExpressionStatementTree)statementTree).expression();
    assertThat(mit.methodSymbol()).isSameAs(declaration);
    assertThat(mit.arguments()).isNotNull();
    assertThat(mit.arguments().openParenToken()).isNotNull();
    assertThat(mit.arguments().closeParenToken()).isNotNull();
  }

  @Test
  void first_token() {
    CompilationUnitTree cut = createTree("""
      class A {
        void bar(){
          foo();
        }
      }
      """);

    ClassTree classTree = (ClassTree) cut.types().get(0);
    MethodInvocationTree mit = (MethodInvocationTree) ((ExpressionStatementTree) ((MethodTree) (classTree.members().get(0))).block().body().get(0)).expression();
    SyntaxToken firstToken = mit.firstToken();
    assertThat(firstToken.text()).isEqualTo("foo");
  }

  @Test
  void first_token_with_type_arguments() {
    CompilationUnitTree cut = createTree("""
      class A {
        void bar(){
          new A().<String>foo();
        }
        <T> void foo() {}
      }
      """);

    ClassTree classTree = (ClassTree) cut.types().get(0);
    MethodInvocationTree mit = (MethodInvocationTree) ((ExpressionStatementTree) ((MethodTree) (classTree.members().get(0))).block().body().get(0)).expression();
    SyntaxToken firstToken = mit.firstToken();
    assertThat(firstToken.text()).isEqualTo("new");
  }

  private static CompilationUnitTree createTree(String code) {
    return JParserTestUtils.parse(code);
  }
}
