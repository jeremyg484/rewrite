package org.openrewrite.java.cleanup;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.version;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class RemoveInstanceOfPatternMatchTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveInstanceOfPatternMatch());
    }

    @Test
    void blockOfStatements() {
        rewriteRun(
                version(
                        java(
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        if (obj instanceof String str) {
                                            System.out.println(str);
                                        }
                                    }
                                }
                                """,
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        if (obj instanceof String) {
                                            String str = (String) obj;
                                            System.out.println(str);
                                        }
                                    }
                                }
                                """),
                        14));
    }

    @Test
    void singleStatement() {
        rewriteRun(
                version(
                        java(
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        if (obj instanceof String str)
                                            System.out.println(str);
                                    }
                                }
                                """,
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        if (obj instanceof String) {
                                            String str = (String) obj;
                                            System.out.println(str);
                                        }
                                    }
                                }
                                """),
                        14));
    }

    @Test
    void emptyStatement() {
        rewriteRun(
                version(
                        java(
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        if (obj instanceof String str);
                                    }
                                }
                                """,
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        if (obj instanceof String);
                                    }
                                }
                                """),
                        14));
    }

    @Test
    void elseStatement() {
        rewriteRun(
                version(
                        java(
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        if (obj instanceof String str) {
                                            System.out.println(str);
                                        } else {
                                            System.out.println();
                                        }
                                    }
                                }
                                """,
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        if (obj instanceof String) {
                                            String str = (String) obj;
                                            System.out.println(str);
                                        } else {
                                            System.out.println();
                                        }
                                    }
                                }
                                """),
                        14));
    }

    @Test
    void qualifiedTypeName() {
        rewriteRun(
                version(
                        java(
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        if (obj instanceof java.lang.String str) {
                                            System.out.println(str);
                                        }
                                    }
                                }
                                """,
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        if (obj instanceof java.lang.String) {
                                            java.lang.String str = (java.lang.String) obj;
                                            System.out.println(str);
                                        }
                                    }
                                }
                                """),
                        14));
    }

    @Test
    void genericType() {
        rewriteRun(
                version(
                        java(
                                """
                                package com.example;

                                import java.util.Collection;
                                import java.util.List;

                                class Example {
                                    public void test(Collection<String> collection) {
                                        if (collection instanceof List<String> list) {
                                            System.out.println(list.size());
                                        }
                                    }
                                }
                                """,
                                """
                                package com.example;

                                import java.util.Collection;
                                import java.util.List;

                                class Example {
                                    public void test(Collection<String> collection) {
                                        if (collection instanceof List<String>) {
                                            List<String> list = (List<String>) collection;
                                            System.out.println(list.size());
                                        }
                                    }
                                }
                                """),
                        14));
    }

    @Test
    void expression() {
        rewriteRun(
                version(
                        java(
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        if (obj.toString() instanceof String str) {
                                            System.out.println(str);
                                        }
                                    }
                                }
                                """,
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        if (obj.toString() instanceof String) {
                                            String str = (String) obj.toString();
                                            System.out.println(str);
                                        }
                                    }
                                }
                                """),
                        14));
    }

    @Test
    void multipleInstanceOf() {
        rewriteRun(
                version(
                        java(
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        if (obj instanceof String str && obj instanceof Integer num) {
                                            System.out.println(str);
                                            System.out.println(num);
                                        }
                                    }
                                }
                                """,
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        if (obj instanceof String && obj instanceof Integer) {
                                            String str = (String) obj;
                                            Integer num = (Integer) obj;
                                            System.out.println(str);
                                            System.out.println(num);
                                        }
                                    }
                                }
                                """),
                        14));
    }

    @Test
    void complexCondition() {
        rewriteRun(
                version(
                        java(
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        if (obj instanceof String str && str.length() > 10) {
                                            System.out.println(str);
                                        }
                                    }
                                }
                                """,
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        if (obj instanceof String && ((String) obj).length() > 10) {
                                            String str = (String) obj;
                                            System.out.println(str);
                                        }
                                    }
                                }
                                """),
                        14));
    }

    @Test
    void noExtraParentheses() {
        rewriteRun(
                version(
                        java(
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        if (obj instanceof String str && str != null) {
                                            System.out.println(str);
                                        }
                                    }
                                }
                                """,
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        if (obj instanceof String && (String) obj != null) {
                                            String str = (String) obj;
                                            System.out.println(str);
                                        }
                                    }
                                }
                                """),
                        14));
    }

    @Test
    void unusedVariable() {
        rewriteRun(
                version(
                        java(
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        if (obj instanceof String str) {
                                            System.out.println();
                                        }
                                    }
                                }
                                """,
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        if (obj instanceof String) {
                                            System.out.println();
                                        }
                                    }
                                }
                                """),
                        14));
    }

    @Test
    void disjunction() {
        rewriteRun(
                version(
                        java(
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        if (obj instanceof String str && str.isEmpty() || false) {
                                            System.out.println();
                                        }
                                    }
                                }
                                """,
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        if (obj instanceof String && ((String) obj).isEmpty() || false) {
                                            System.out.println();
                                        }
                                    }
                                }
                                """),
                        14));
    }

    @Test
    void localVariable() {
        rewriteRun(
                version(
                        java(
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        if (obj instanceof String str || true) {
                                            String str = null;
                                            System.out.println(str);
                                        }
                                    }
                                }
                                """,
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        if (obj instanceof String || true) {
                                            String str = null;
                                            System.out.println(str);
                                        }
                                    }
                                }
                                """),
                        14));
    }

    @Test
    void negationLocalVariable() {
        rewriteRun(
                version(
                        java(
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        if (!(obj instanceof String str)) {
                                            Integer str = null;
                                            System.out.println(str);
                                        } else {
                                            System.out.println(str);
                                        }
                                    }
                                }
                                """,
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        if (!(obj instanceof String)) {
                                            String str = null;
                                            System.out.println(str);
                                        } else {
                                            String str = (String) obj;
                                            System.out.println(str);
                                        }
                                    }
                                }
                                """),
                        14));
    }

    @Test
    void sameNamedVariables() {
        rewriteRun(
                version(
                        java(
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        if (obj instanceof String v && v.isEmpty() || obj instanceof Integer v && v.equals(1)) {
                                            System.out.println();
                                        }
                                    }
                                }
                                """,
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        if (obj instanceof String && ((String) obj).isEmpty() || obj instanceof Integer && ((Integer) obj).equals(1)) {
                                            System.out.println();
                                        }
                                    }
                                }
                                """),
                        14));
    }

    @Test
    void nestedComplexCondition() {
        rewriteRun(
                version(
                        java(
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        if ((1 != 2 && obj instanceof String str && str.isEmpty()) && (str.length() > 10 || 1 == 2)) {
                                            System.out.println(str);
                                        }
                                    }
                                }
                                """,
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        if ((1 != 2 && obj instanceof String && ((String) obj).isEmpty()) && (((String) obj).length() > 10 || 1 == 2)) {
                                            String str = (String) obj;
                                            System.out.println(str);
                                        }
                                    }
                                }
                                """),
                        14));
    }

    @Test
    void expressionAndComplexCondition() {
        rewriteRun(
                version(
                        java(
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        if (obj.toString() instanceof String str && str.length() > 10) {
                                            System.out.println(str);
                                        }
                                    }
                                }
                                """,
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        if (obj.toString() instanceof String && ((String) obj.toString()).length() > 10) {
                                            String str = (String) obj.toString();
                                            System.out.println(str);
                                        }
                                    }
                                }
                                """),
                        14));
    }

    @Test
    void sequentialIfs() {
        rewriteRun(
                version(
                        java(
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        if (obj instanceof String v) {
                                            System.out.println(v);
                                        }
                                        if (obj instanceof Integer v) {
                                            System.out.println(v);
                                        }
                                    }
                                }
                                """,
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        if (obj instanceof String) {
                                            String v = (String) obj;
                                            System.out.println(v);
                                        }
                                        if (obj instanceof Integer) {
                                            Integer v = (Integer) obj;
                                            System.out.println(v);
                                        }
                                    }
                                }
                                """),
                        14));
    }

    @Test
    void nestedIfs() {
        rewriteRun(
                version(
                        java(
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        if (obj instanceof String v) {
                                            System.out.println(v);
                                            if (obj instanceof Integer v2) {
                                                System.out.println(v2);
                                            }
                                        }
                                    }
                                }
                                """,
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        if (obj instanceof String) {
                                            String v = (String) obj;
                                            System.out.println(v);
                                            if (obj instanceof Integer) {
                                                Integer v2 = (Integer) obj;
                                                System.out.println(v2);
                                            }
                                        }
                                    }
                                }
                                """),
                        14));
    }

    @Test
    void instanceOfPattern() {
        rewriteRun(
                version(
                        java(
                                """
                                package com.example;

                                import java.time.LocalDate;
                                import java.time.temporal.Temporal;

                                class Example {
                                    public void test(Object obj) {
                                        if (obj instanceof Temporal t && t instanceof LocalDate d) {
                                            System.out.println(d);
                                        }
                                    }
                                }
                                """,
                                """
                                package com.example;

                                import java.time.LocalDate;
                                import java.time.temporal.Temporal;

                                class Example {
                                    public void test(Object obj) {
                                        if (obj instanceof Temporal && ((Temporal) obj) instanceof LocalDate) {
                                            LocalDate d = (LocalDate) ((Temporal) obj);
                                            System.out.println(d);
                                        }
                                    }
                                }
                                """),
                        14));
    }

    @Test
    void negation() {
        rewriteRun(
                version(
                        java(
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        if (!(obj instanceof String str)) {
                                            System.out.println(obj);
                                        } else {
                                            System.out.println(str);
                                        }
                                    }
                                }
                                """,
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        if (!(obj instanceof String)) {
                                            System.out.println(obj);
                                        } else {
                                            String str = (String) obj;
                                            System.out.println(str);
                                        }
                                    }
                                }
                                """),
                        14));
    }

    @Test
    void emptyElseStatement() {
        rewriteRun(
                version(
                        java(
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        if (!(obj instanceof String str)) {
                                            System.out.println(obj);
                                        } else;
                                    }
                                }
                                """,
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        if (!(obj instanceof String)) {
                                            System.out.println(obj);
                                        } else;
                                    }
                                }
                                """),
                        14));
    }

    @Test
    void singleElseStatement() {
        rewriteRun(
                version(
                        java(
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        if (!(obj instanceof String str)) {
                                            System.out.println(obj);
                                        } else
                                            System.out.println(str);
                                    }
                                }
                                """,
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        if (!(obj instanceof String)) {
                                            System.out.println(obj);
                                        } else {
                                            String str = (String) obj;
                                            System.out.println(str);
                                        }
                                    }
                                }
                                """),
                        14));
    }

    @Test
    void doubleNegation() {
        rewriteRun(
                version(
                        java(
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        if (!!(obj instanceof String str)) {
                                            System.out.println(str);
                                        } else {
                                            System.out.println(obj);
                                        }
                                    }
                                }
                                """,
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        if (!!(obj instanceof String)) {
                                            String str = (String) obj;
                                            System.out.println(str);
                                        } else {
                                            System.out.println(obj);
                                        }
                                    }
                                }
                                """),
                        14));
    }

    @Test
    void negationCancellation() {
        rewriteRun(
                version(
                        java(
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        if (!(obj instanceof String str) && obj instanceof String str) {
                                            System.out.println(str);
                                        } else {
                                            System.out.println(obj);
                                        }
                                    }
                                }
                                """,
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        if (!(obj instanceof String) && obj instanceof String) {
                                            String str = (String) obj;
                                            System.out.println(str);
                                        } else {
                                            System.out.println(obj);
                                        }
                                    }
                                }
                                """),
                        14));
    }

    @Test
    void ternary() {
        rewriteRun(
                version(
                        java(
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        System.out.println(obj instanceof String str ? str : null);
                                    }
                                }
                                """,
                                """
                                package com.example;

                                class Example {
                                    public void test(Object obj) {
                                        System.out.println(obj instanceof String ? (String) obj : null);
                                    }
                                }
                                """),
                        14));
    }

}