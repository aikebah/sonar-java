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
package org.sonar.java.checks;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.sonar.check.Rule;
import org.sonar.plugins.java.api.IssuableSubscriptionVisitor;
import org.sonar.plugins.java.api.semantic.SymbolMetadata;
import org.sonar.plugins.java.api.semantic.Type;
import org.sonar.plugins.java.api.tree.MethodTree;
import org.sonar.plugins.java.api.tree.Tree;
import org.sonar.plugins.java.api.tree.TypeTree;
import org.sonar.plugins.java.api.tree.VariableTree;

@Rule(key = "S3553")
public class OptionalAsParameterCheck extends IssuableSubscriptionVisitor {

  private static final String JAVA_UTIL_OPTIONAL = "java.util.Optional";
  private static final String GUAVA_OPTIONAL = "com.google.common.base.Optional";
  private static final List<String> PRIMITIVE_OPTIONALS = Arrays.asList(
    "java.util.OptionalDouble",
    "java.util.OptionalInt",
    "java.util.OptionalLong");

  @Override
  public List<Tree.Kind> nodesToVisit() {
    return Arrays.asList(Tree.Kind.METHOD, Tree.Kind.CONSTRUCTOR);
  }

  @Override
  public void visitNode(Tree tree) {
    final var methodTree = (MethodTree) tree;

    // If the method is overriding something, the user has no control over the parameter type here, so we should not raise an issue.
    if (Boolean.FALSE.equals(methodTree.isOverriding())) {

      for (VariableTree parameter : methodTree.parameters()) {
        SymbolMetadata parameterMetadata = parameter.symbol().metadata();
        if (parameterMetadata.isAnnotatedWith("org.springframework.web.bind.annotation.RequestParam")
          || parameterMetadata.isAnnotatedWith("org.springframework.web.bind.annotation.PathVariable")) {
          continue;
        }

        TypeTree typeTree = parameter.type();
        Optional<String> msg = expectedTypeInsteadOfOptional(typeTree.symbolType());
        msg.ifPresent(s -> reportIssue(typeTree, s));
      }
    }
  }

  private static Optional<String> expectedTypeInsteadOfOptional(Type type) {
    if (type.is(JAVA_UTIL_OPTIONAL) || type.is(GUAVA_OPTIONAL)) {
      String msg;
      if (type.isParameterized()) {
        String parameterTypeName = type.typeArguments().get(0).erasure().name();
        msg = formatMsg(parameterTypeName);
      } else {
        msg = "Specify a type instead.";
      }
      return Optional.of(msg);
    }
    return PRIMITIVE_OPTIONALS.stream()
      .filter(type::is)
      .findFirst()
      .map(optional -> formatMsg(optional.substring(JAVA_UTIL_OPTIONAL.length()).toLowerCase()));
  }

  private static String formatMsg(String typeName) {
    return "Specify a \"" + typeName + "\" parameter instead.";
  }

}
