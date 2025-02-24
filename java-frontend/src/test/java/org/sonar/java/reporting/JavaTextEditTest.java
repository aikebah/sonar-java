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
package org.sonar.java.reporting;

import java.io.File;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sonar.java.model.JParserTestUtils;
import org.sonar.java.model.expression.LiteralTreeImpl;
import org.sonar.plugins.java.api.tree.ClassTree;
import org.sonar.plugins.java.api.tree.CompilationUnitTree;
import org.sonar.plugins.java.api.tree.LiteralTree;
import org.sonar.plugins.java.api.tree.Tree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JavaTextEditTest {
  private static final File JAVA_FILE = new File("src/test/files/api/JavaFileInternalJavaIssueBuilderTest.java");
  private static ClassTree classTree;

  @BeforeAll
  static void setup() {
    CompilationUnitTree compilationUnitTree = JParserTestUtils.parse(JAVA_FILE);
    classTree = (ClassTree) compilationUnitTree.types().get(0);
  }

  @Test
  void test_remove() {
    JavaTextEdit javaTextEdit = JavaTextEdit.removeTree(classTree.members().get(0));
    assertThat(javaTextEdit.getReplacement()).isEmpty();
    assertTextSpan(javaTextEdit.getTextSpan(), 4, 2, 4, 13);
  }

  @Test
  void test_replace_tree() {
    JavaTextEdit javaTextEdit = JavaTextEdit.replaceTree(classTree.members().get(0), "replacement");
    assertThat(javaTextEdit.getReplacement()).isEqualTo("replacement");
    assertTextSpan(javaTextEdit.getTextSpan(), 4, 2, 4, 13);
  }

  @Test
  void test_insert_before_tree() {
    JavaTextEdit javaTextEdit = JavaTextEdit.insertBeforeTree(classTree.members().get(0), "replacement");
    assertThat(javaTextEdit.getReplacement()).isEqualTo("replacement");
    assertTextSpan(javaTextEdit.getTextSpan(), 4, 2, 4, 2);
  }

  @Test
  void test_insert_before_no_token_tree() {
    LiteralTree brokenLiteral = new LiteralTreeImpl(Tree.Kind.STRING_LITERAL, null);
    assertThatThrownBy(() -> JavaTextEdit.insertBeforeTree(brokenLiteral, "replacement"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Trying to insert a quick fix before a Tree without token.");
  }

  @Test
  void test_insert_after_tree() {
    JavaTextEdit javaTextEdit = JavaTextEdit.insertAfterTree(classTree.members().get(0), "replacement");
    assertThat(javaTextEdit.getReplacement()).isEqualTo("replacement");
    assertTextSpan(javaTextEdit.getTextSpan(), 4, 13, 4, 13);
  }

  @Test
  void test_insert_after_no_token_tree() {
    LiteralTree brokenLiteral = new LiteralTreeImpl(Tree.Kind.STRING_LITERAL, null);
    assertThatThrownBy(() -> JavaTextEdit.insertAfterTree(brokenLiteral, "replacement"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Trying to insert a quick fix after a Tree without token.");
  }

  @Test
  void test_replace_between_tree() {
    Tree firstMember = classTree.members().get(0);
    JavaTextEdit javaTextEdit = JavaTextEdit.replaceBetweenTree(firstMember.firstToken(), firstMember.lastToken(), "replacement");
    assertThat(javaTextEdit.getReplacement()).isEqualTo("replacement");
    assertTextSpan(javaTextEdit.getTextSpan(), 4, 2, 4, 13);
  }
  
  @Test
  void test_replace_between_tree_exclude_edges() {
    Tree firstMember = classTree.members().get(0);
    JavaTextEdit javaTextEdit = JavaTextEdit.replaceBetweenTree(firstMember.firstToken(), false, firstMember.lastToken(), false, "replacement");
    assertThat(javaTextEdit.getReplacement()).isEqualTo("replacement");
    assertTextSpan(javaTextEdit.getTextSpan(), 4, 5, 4, 12);
    
    javaTextEdit = JavaTextEdit.replaceBetweenTree(firstMember.firstToken(), false, firstMember.lastToken(), true, "replacement");
    assertTextSpan(javaTextEdit.getTextSpan(), 4, 5, 4, 13);
    
    javaTextEdit = JavaTextEdit.replaceBetweenTree(firstMember.firstToken(), true, firstMember.lastToken(), false, "replacement");
    assertTextSpan(javaTextEdit.getTextSpan(), 4, 2, 4, 12);
    
    javaTextEdit = JavaTextEdit.replaceBetweenTree(firstMember.firstToken(), true, firstMember.lastToken(), true, "replacement");
    assertTextSpan(javaTextEdit.getTextSpan(), 4, 2, 4, 13);
  }

  @Test
  void test_remove_between_tree() {
    Tree firstMember = classTree.members().get(0);
    JavaTextEdit javaTextEdit = JavaTextEdit.removeBetweenTree(firstMember.firstToken(), firstMember.lastToken());
    assertThat(javaTextEdit.getReplacement()).isEmpty();
    assertTextSpan(javaTextEdit.getTextSpan(), 4, 2, 4, 13);
  }

  private static void assertTextSpan(AnalyzerMessage.TextSpan textSpan, int startLine, int startColumn, int endLine, int endColumn) {
      assertThat(textSpan.startLine).as("Start line").isEqualTo(startLine);
      assertThat(textSpan.startCharacter).as("Start character").isEqualTo(startColumn);
      assertThat(textSpan.endLine).as("End line").isEqualTo(endLine);
      assertThat(textSpan.endCharacter).as("End character").isEqualTo(endColumn);
    }
}
