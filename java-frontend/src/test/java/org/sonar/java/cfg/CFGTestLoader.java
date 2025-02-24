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
package org.sonar.java.cfg;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Assertions;
import org.sonar.java.model.JParserTestUtils;
import org.sonar.plugins.java.api.tree.ClassTree;
import org.sonar.plugins.java.api.tree.CompilationUnitTree;
import org.sonar.plugins.java.api.tree.MethodTree;
import org.sonar.plugins.java.api.tree.Tree;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class CFGTestLoader {

  private CompilationUnitTree compiledTest;

  public CFGTestLoader(String fileName) {
    final File file = new File(fileName);
    try (StringWriter buffer = new StringWriter(); PrintWriter printer = new PrintWriter(buffer);) {
      List<String> lines = FileUtils.readLines(file, StandardCharsets.UTF_8);
      for (String line : lines) {
        printer.println(line);
      }
      printer.flush();
      compiledTest = JParserTestUtils.parse(buffer.toString());
    } catch (Exception e) {
      Assertions.fail("Unable to compile file " + file.getAbsolutePath());
    }
  }

  public MethodTree getMethod(String className, String methodName) {
    for (Tree type : compiledTest.types()) {
      if (type.is(Tree.Kind.CLASS)) {
        ClassTree classTree = (ClassTree) type;
        if (className.equals(classTree.simpleName().name())) {
          for (Tree member : classTree.members()) {
            if (member.is(Tree.Kind.METHOD)) {
              MethodTree method = (MethodTree) member;
              if (methodName.equals(method.simpleName().name())) {
                return method;
              }
            }
          }
        }
      }
    }
    Assertions.fail("Method " + methodName + " of class " + className + " not found!");
    return null;
  }
}
