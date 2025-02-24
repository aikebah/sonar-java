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

import org.sonar.check.Rule;
import org.sonar.check.RuleProperty;
import org.sonar.java.checks.helpers.ExpressionsHelper;
import org.sonar.java.metrics.MetricsScannerContext;
import org.sonar.plugins.java.api.IssuableSubscriptionVisitor;
import org.sonar.plugins.java.api.semantic.Symbol;
import org.sonar.plugins.java.api.semantic.Type;
import org.sonar.plugins.java.api.tree.ClassTree;
import org.sonar.plugins.java.api.tree.Tree;
import org.sonar.plugins.java.api.tree.Tree.Kind;

import java.util.Arrays;
import java.util.List;

@Rule(key = "S2972")
public class InnerClassTooManyLinesCheck extends IssuableSubscriptionVisitor {

  private static final int DEFAULT_MAX = 25;

  @RuleProperty(key = "Max",
    description = "The maximum number of lines allowed",
    defaultValue = "" + DEFAULT_MAX)
  public int max = DEFAULT_MAX;

  @Override
  public List<Kind> nodesToVisit() {
    return Arrays.asList(Kind.CLASS, Kind.ENUM, Kind.INTERFACE, Kind.ANNOTATION_TYPE);
  }

  @Override
  public void visitNode(Tree tree) {
    ClassTree node = (ClassTree) tree;
    Symbol.TypeSymbol symbol = node.symbol();
    Symbol owner = symbol.owner();
    Type ownerType = owner.type();
    if (ownerType != null && ownerType.isClass() && owner.owner().isPackageSymbol()) {
      // raise only one issue for the first level of nesting when multiple nesting
      var metricsComputer = ((MetricsScannerContext)context).getMetricsComputer();
      int lines = metricsComputer.getLinesOfCode(node);
      if (lines > max) {
        reportIssue(ExpressionsHelper.reportOnClassTree(node), "Reduce this class from " + lines +
          " lines to the maximum allowed " + max + " or externalize it in a public class.");
      }
    }
  }

}
