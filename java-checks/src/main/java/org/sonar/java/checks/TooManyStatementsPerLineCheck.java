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

import java.util.HashMap;
import java.util.Map;
import org.sonar.check.Rule;
import org.sonar.java.model.LineUtils;
import org.sonar.plugins.java.api.IssuableSubscriptionVisitor;
import org.sonar.plugins.java.api.tree.AssertStatementTree;
import org.sonar.plugins.java.api.tree.BaseTreeVisitor;
import org.sonar.plugins.java.api.tree.BlockTree;
import org.sonar.plugins.java.api.tree.BreakStatementTree;
import org.sonar.plugins.java.api.tree.ClassTree;
import org.sonar.plugins.java.api.tree.ContinueStatementTree;
import org.sonar.plugins.java.api.tree.DoWhileStatementTree;
import org.sonar.plugins.java.api.tree.ExpressionStatementTree;
import org.sonar.plugins.java.api.tree.ForEachStatement;
import org.sonar.plugins.java.api.tree.ForStatementTree;
import org.sonar.plugins.java.api.tree.IfStatementTree;
import org.sonar.plugins.java.api.tree.MethodTree;
import org.sonar.plugins.java.api.tree.ReturnStatementTree;
import org.sonar.plugins.java.api.tree.StatementTree;
import org.sonar.plugins.java.api.tree.SwitchStatementTree;
import org.sonar.plugins.java.api.tree.SynchronizedStatementTree;
import org.sonar.plugins.java.api.tree.SyntaxToken;
import org.sonar.plugins.java.api.tree.ThrowStatementTree;
import org.sonar.plugins.java.api.tree.Tree;
import org.sonar.plugins.java.api.tree.Tree.Kind;
import org.sonar.plugins.java.api.tree.TryStatementTree;
import org.sonar.plugins.java.api.tree.VariableTree;
import org.sonar.plugins.java.api.tree.WhileStatementTree;

import java.util.Arrays;
import java.util.List;
import org.sonarsource.analyzer.commons.annotations.DeprecatedRuleKey;

@DeprecatedRuleKey(ruleKey = "S00122", repositoryKey = "squid")
@Rule(key = "S122")
public class TooManyStatementsPerLineCheck extends IssuableSubscriptionVisitor {

  @Override
  public List<Kind> nodesToVisit() {
    return Arrays.asList(Tree.Kind.METHOD, Tree.Kind.CONSTRUCTOR, Tree.Kind.STATIC_INITIALIZER, Kind.INITIALIZER);
  }

  @Override
  public void visitNode(Tree tree) {
    BlockTree block;
    if (tree.is(Tree.Kind.STATIC_INITIALIZER, Kind.INITIALIZER)) {
      block = (BlockTree) tree;
    } else {
      block = ((MethodTree) tree).block();
    }
    if (block != null) {
      StatementVisitor visitor = new StatementVisitor();
      block.accept(visitor);
      visitor.statementsPerLine.forEach((line, count) -> {
        if (count > 1) {
          addIssue(line, "At most one statement is allowed per line, but " + count + " statements were found on this line.");
        }
      });
    }
  }

  private static class StatementVisitor extends BaseTreeVisitor {
    private final Map<Integer, Integer> statementsPerLine = new HashMap<>();

    @Override
    public void visitClass(ClassTree tree) {
      // do nothing, as inner class will be visited later
    }

    @Override
    public void visitAssertStatement(AssertStatementTree tree) {
      addLines(tree.assertKeyword(), tree.semicolonToken());
    }

    @Override
    public void visitBreakStatement(BreakStatementTree tree) {
      addLines(tree.breakKeyword(), tree.semicolonToken());
    }

    @Override
    public void visitContinueStatement(ContinueStatementTree tree) {
      addLines(tree.continueKeyword(), tree.semicolonToken());
    }

    @Override
    public void visitReturnStatement(ReturnStatementTree tree) {
      addLines(tree.returnKeyword(), tree.semicolonToken());
    }

    @Override
    public void visitThrowStatement(ThrowStatementTree tree) {
      addLines(tree.throwKeyword(), tree.semicolonToken());
    }

    @Override
    public void visitExpressionStatement(ExpressionStatementTree tree) {
      SyntaxToken firstToken = tree.firstToken();
      if (firstToken != null) {
        addLines(firstToken, tree.semicolonToken());
      } else {
        addLine(tree.semicolonToken());
      }
    }

    @Override
    public void visitIfStatement(IfStatementTree tree) {
      addLine(tree.ifKeyword());
      StatementTree thenStatement = tree.thenStatement();
      StatementTree elseStatement = tree.elseStatement();
      scan(thenStatement);
      scan(elseStatement);
      if (elseStatement == null) {
        addLineOfCloseBrace(tree.ifKeyword(), thenStatement);
      } else {
        addLineOfCloseBrace(tree.ifKeyword(), elseStatement);
      }
    }

    private void addLineOfCloseBrace(SyntaxToken startToken, StatementTree tree) {
      if (tree.is(Tree.Kind.BLOCK)) {
        SyntaxToken closeBraceToken = ((BlockTree) tree).closeBraceToken();
        if (LineUtils.startLine(startToken) != LineUtils.startLine(closeBraceToken) &&
          !statementsPerLine.containsKey(LineUtils.startLine(closeBraceToken))) {
          addLine(closeBraceToken);
        }
      }
    }

    @Override
    public void visitSynchronizedStatement(SynchronizedStatementTree tree) {
      addLine(tree.synchronizedKeyword());
      addLineOfCloseBrace(tree.synchronizedKeyword(), tree.block());
      scan(tree.block());
    }

    @Override
    public void visitSwitchStatement(SwitchStatementTree tree) {
      addLines(tree.switchKeyword(), tree.openBraceToken());
      scan(tree.cases());
      addLine(tree.closeBraceToken());
    }

    @Override
    public void visitVariable(VariableTree tree) {
      SyntaxToken endToken = tree.endToken();
      if (endToken != null && ";".equals(endToken.text())) {
        addLine(endToken);
      }
    }

    @Override
    public void visitWhileStatement(WhileStatementTree tree) {
      // do not scan the condition
      addLines(tree.whileKeyword(), tree.closeParenToken());
      addLineOfCloseBrace(tree.whileKeyword(), tree.statement());
      scan(tree.statement());
    }

    @Override
    public void visitDoWhileStatement(DoWhileStatementTree tree) {
      // do not scan the condition
      addLine(tree.doKeyword());
      if (LineUtils.startLine(tree.doKeyword()) != LineUtils.startLine(tree.whileKeyword())) {
        addLines(tree.whileKeyword(), tree.semicolonToken());
      }
      scan(tree.statement());
    }

    @Override
    public void visitForStatement(ForStatementTree tree) {
      // do not scan the initializer, updater and condition
      addLines(tree.forKeyword(), tree.closeParenToken());
      addLineOfCloseBrace(tree.forKeyword(), tree.statement());
      scan(tree.statement());
    }

    @Override
    public void visitForEachStatement(ForEachStatement tree) {
      // do not scan the variable and expression
      addLines(tree.forKeyword(), tree.closeParenToken());
      addLineOfCloseBrace(tree.forKeyword(), tree.statement());
      scan(tree.statement());
    }

    @Override
    public void visitTryStatement(TryStatementTree tree) {
      // do not scan resources
      if (tree.resourceList().isEmpty()) {
        addLine(tree.tryKeyword());
      } else {
        addLines(tree.tryKeyword(), tree.closeParenToken());
      }
      scan(tree.block());
      scan(tree.catches());
      if (tree.finallyKeyword() != null) {
        addLine(tree.finallyKeyword());
      }
      scan(tree.finallyBlock());
    }

    private void addLine(SyntaxToken token) {
      statementsPerLine.compute(LineUtils.startLine(token), (k, v) -> (v == null) ? 1 : (v + 1));
    }

    private void addLines(SyntaxToken startToken, SyntaxToken endToken) {
      addLine(startToken);
      if (LineUtils.startLine(startToken) != LineUtils.startLine(endToken)) {
        addLine(endToken);
      }
    }
  }
}
