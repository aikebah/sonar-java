/*
 * SonarQube Java
 * Copyright (C) 2012-2020 SonarSource SA
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
package org.sonar.java.se.checks;

import org.junit.jupiter.api.Test;
import org.sonar.java.se.SETestUtils;
import org.sonar.java.testing.CheckVerifier;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

class OptionalGetBeforeIsPresentCheckTest {

  @Test
  void test() {
    CheckVerifier.newVerifier()
      .onFile("src/test/files/se/OptionalGetBeforeIsPresentCheck.java")
      .withCheck(new OptionalGetBeforeIsPresentCheck())
      .withClassPath(SETestUtils.CLASS_PATH)
      .verifyIssues();
  }

  @Test
  void test_with_jdk_more_recent_than_8() {
    assumeTrue(!System.getProperty("java.version").startsWith("1.8"));
    CheckVerifier.newVerifier()
      .onFile("src/test/files/se/OptionalGetBeforeIsPresentCheck_jdk11.java")
      .withCheck(new OptionalGetBeforeIsPresentCheck())
      .withClassPath(SETestUtils.CLASS_PATH)
      .verifyIssues();
  }

  @Test
  void invocation_leading_to_NoSuchElementException() {
    CheckVerifier.newVerifier()
      .onFile("src/test/files/se/MethodInvocationLeadingToNSEE.java")
      .withCheck(new OptionalGetBeforeIsPresentCheck())
      .withClassPath(SETestUtils.CLASS_PATH)
      .verifyIssues();
  }

}
