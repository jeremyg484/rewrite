/*
 * Copyright 2022 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.tree.J;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.RewriteTest.toRecipe;

@SuppressWarnings({"UnnecessaryBoxing", "PatternVariableCanBeUsed"})
class JavaTemplateTest7Test implements RewriteTest {

    @Issue("https://github.com/openrewrite/rewrite/issues/1198")
    @Test
    @SuppressWarnings({
      "CachedNumberConstructorCall",
      "Convert2MethodRef"
      , "removal"})
    void lambdaIsVariableInitializer() {
        rewriteRun(
          spec -> spec.recipe(toRecipe(() -> new JavaVisitor<>() {
              final MethodMatcher matcher = new MethodMatcher("Integer valueOf(..)");
              final JavaTemplate t = JavaTemplate.builder("new Integer(#{any()})").build();

              @Override
              public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                  if (matcher.matches(method)) {
                      return method.withTemplate(t, getCursor(), method.getCoordinates().replace(), method.getArguments().get(0));
                  }
                  return super.visitMethodInvocation(method, p);
              }
          })),
          java(
            """
              import java.util.function.Function;
              class Test {
                  Function<String, Integer> asInteger = it -> Integer.valueOf(it);
              }
              """,
            """
              import java.util.function.Function;
              class Test {
                  Function<String, Integer> asInteger = it -> new Integer(it);
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1505")
    @Test
    void methodDeclarationWithComment() {
        rewriteRun(
          spec -> spec.recipe(toRecipe(() -> new JavaVisitor<>() {
              @Override
              public J visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
                  var cd = classDecl;
                  if (cd.getBody().getStatements().isEmpty()) {
                      cd = cd.withBody(
                        cd.getBody().withTemplate(
                          JavaTemplate.builder(
                              //language=groovy
                              """
                                /**
                                 * comment
                                 */
                                void foo() {
                                }
                                """
                            )
                            .context(getCursor())
                            .build(),
                          getCursor(),
                          cd.getBody().getCoordinates().firstStatement()
                        )
                      );
                  }
                  return cd;
              }
          })),
          java(
            """
              class A {

              }
              """,
            """
              class A {
                  /**
                   * comment
                   */
                  void foo() {
                  }

              }
              """
          )
        );
    }

    @SuppressWarnings("UnusedAssignment")
    @Issue("https://github.com/openrewrite/rewrite/issues/1821")
    @Test
    void assignmentNotPartOfVariableDeclaration() {
        rewriteRun(
          spec -> spec.recipe(toRecipe(() -> new JavaIsoVisitor<>() {
              @Override
              public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext p) {
                  var a = assignment;
                  if (a.getAssignment() instanceof J.MethodInvocation) {
                      J.MethodInvocation mi = (J.MethodInvocation) a.getAssignment();
                      a = a.withAssignment(mi.withTemplate(
                        JavaTemplate.builder("1").build(),
                        getCursor(),
                        mi.getCoordinates().replace()
                      ));
                  }
                  return a;
              }
          })),
          java(
            """
              class A {
                  void foo() {
                      int i;
                      i = Integer.valueOf(1);
                  }
              }
              """,
            """
              class A {
                  void foo() {
                      int i;
                      i = 1;
                  }
              }
              """
          )
        );
    }
}
