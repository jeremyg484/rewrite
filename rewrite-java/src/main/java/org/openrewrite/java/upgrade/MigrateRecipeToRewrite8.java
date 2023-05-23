/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.java.upgrade;

import lombok.Value;
import lombok.With;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.*;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Marker;
import org.openrewrite.marker.Markers;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.openrewrite.Tree.randomId;
import static org.openrewrite.java.tree.J.Modifier.Type.Protected;
import static org.openrewrite.java.tree.J.Modifier.Type.Public;

public class MigrateRecipeToRewrite8 extends Recipe {

    private static final String REWRITE_RECIPE_FQN = "org.openrewrite.Recipe";
    private static final String JAVA_ISO_VISITOR_FQN = "org.openrewrite.java.JavaIsoVisitor";
    private static final String JAVA_VISITOR_FQN = "org.openrewrite.java.JavaVisitor";
    private static final MethodMatcher GET_SINGLE_SOURCE_APPLICABLE_TEST_METHOD_MATCHER = new MethodMatcher("org.openrewrite.Recipe getSingleSourceApplicableTest()", true);
    private static final MethodMatcher GET_APPLICABLE_TEST_METHOD_MATCHER = new MethodMatcher("org.openrewrite.Recipe getApplicableTest()", true);
    private static final MethodMatcher GET_VISITOR_METHOD_MATCHER = new MethodMatcher("org.openrewrite.Recipe getVisitor()", true);
    private static final MethodMatcher VISIT_METHOD_MATCHER = new MethodMatcher("org.openrewrite.Recipe visit(..)", true);
    private static final AnnotationMatcher OVERRIDE_ANNOTATION_MATCHER = new AnnotationMatcher("@java.lang.Override");
    private static final MethodMatcher VISIT_JAVA_SOURCE_FILE_METHOD_MATCHER = new MethodMatcher("org.openrewrite.java.JavaVisitor visitJavaSourceFile(..)", true);
    private static final MethodMatcher TREE_VISITOR_VISIT_METHOD_MATCHER = new MethodMatcher("org.openrewrite.TreeVisitor visit(..)", true);
    private static final MethodMatcher APPLICABILITY_AND_METHOD_MATCHER = new MethodMatcher("org.openrewrite.Applicability and(..)");
    private static final MethodMatcher APPLICABILITY_OR_METHOD_MATCHER = new MethodMatcher("org.openrewrite.Applicability or(..)");
    private static final MethodMatcher APPLICABILITY_NOT_METHOD_MATCHER = new MethodMatcher("org.openrewrite.Applicability not(..)");
    private static final MethodMatcher DO_NEXT_METHOD_MATCHER = new MethodMatcher("org.openrewrite.Recipe doNext(..)");

    private static J.ParameterizedType getVisitorReturnTypeTemplate;
    private static J.MethodDeclaration visitTreeMethodDeclarationTemplate;
    private static J.MethodInvocation visitTreeMethodInvocationTemplate;
    private static J.TypeCast visitTreeMethodInvocationTypeCastTemplate;
    private static J.MethodInvocation preconditionAndTemplate;
    private static J.MethodInvocation preconditionOrTemplate;
    private static J.MethodInvocation preconditionNotTemplate;
    private static J.MemberReference visitMemberReferenceTemplate;

    public static final String MIGRATION_GUIDE_URL = "https://to-be-written";
    private static final String PLEASE_FOLLOW_MIGRATION_GUIDE = "please follow the migration guide here: " + MigrateRecipeToRewrite8.MIGRATION_GUIDE_URL;
    private static final String VISIT_SOURCE_FILES_COMMENT = " [Rewrite8 migration] This recipe uses the visit multiple sources method " +
                                                             "`visit(List<SourceFile> before, P p)`, " +
                                                             "needs to be migrated to use new introduced scanning recipe, " + MigrateRecipeToRewrite8.PLEASE_FOLLOW_MIGRATION_GUIDE;
    private static final String DO_NEXT_COMMENT = " [Rewrite8 migration] Method `Recipe#doNext(..)` is removed, you might want to change the recipe to be a scanning recipe, or just simply replace to use `TreeVisitor#doAfterVisit`, " +
                                                  MigrateRecipeToRewrite8.PLEASE_FOLLOW_MIGRATION_GUIDE;
    private static final String APPLICABLE_TEST_COMMENT = " [Rewrite8 migration] Method `Recipe#getApplicableTest(..)" +
                                                          "` is deprecated and needs to be converted to a " +
                                                          "`ScanningRecipe`. Or you can use `Precondition#check()` if" +
                                                          " it is meant to use a single-source applicability test. " +
                                                          MigrateRecipeToRewrite8.PLEASE_FOLLOW_MIGRATION_GUIDE;
    private static final String COMPLEX_SINGLE_SOURCE_APPLICABLE_TEST_COMMENT = " [Rewrite8 migration] This getSingleSourceApplicableTest methods might have multiple returns, need manually migrate to use `Precondition#check()`, " +
                                                                                MigrateRecipeToRewrite8.PLEASE_FOLLOW_MIGRATION_GUIDE;

    private static final String VISIT_TREE_METHOD_TEMPLATE_CODE = "import org.openrewrite.Tree;\n" +
        "import org.openrewrite.internal.lang.NonNull;\n" +
        "import org.openrewrite.internal.lang.Nullable;\n" +
        "import org.openrewrite.java.JavaIsoVisitor;\n" +
        "import org.openrewrite.java.tree.J;\n" +
        "import org.openrewrite.java.tree.JavaSourceFile;\n" +
        "public class A<P> extends JavaIsoVisitor<P> {\n" +
        "    @Override\n" +
        "    public @Nullable J visit(@Nullable Tree tree, @NonNull P p) {\n" +
        "        if (tree instanceof JavaSourceFile) {\n" +
        "            JavaSourceFile toBeReplaced = (JavaSourceFile) tree;\n" +
        "        }\n" +
        "        return super.visit(tree, p);\n" +
        "    }\n" +
        "}";

    @Override
    public String getDisplayName() {
        return "Migrate Rewrite recipes from version 7 to 8";
    }

    @Override
    public String getDescription() {
        return "Rewrite Recipe Migration to version 8. While most parts can be automatically migrated, there are some" +
               " complex and open-ended scenarios that require manual attention. In those cases, this recipe will add" +
               " a comment to the code and request a human to review and handle it manually.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("Rewrite8 migration");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            boolean hasApplicableTest = false;
            final List<Statement> applicableTestMethodStatements = new ArrayList<>();
            boolean applicableTestMethodHasMultipleReturns = false;

            @Override
            public J.MemberReference visitMemberReference(final J.MemberReference memberRef,
                                                          final ExecutionContext executionContext) {
                if (MigrateRecipeToRewrite8.VISIT_JAVA_SOURCE_FILE_METHOD_MATCHER.matches(memberRef.getMethodType())) {
                    maybeAddImport("org.openrewrite.TreeVisitor");
                    return getVisitMemberReferenceTemplate();
                }
                return memberRef;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method,
                                                            ExecutionContext ctx) {
                if (MigratedTo8.hasMarker(method)) {
                    return method;
                }

                method = super.visitMethodInvocation(method, ctx);
                if (DO_NEXT_METHOD_MATCHER.matches(method.getMethodType())) {
                    return MigratedTo8.withMarker( (J.MethodInvocation)commentOf(method, DO_NEXT_COMMENT));
                }
                return method;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl,
                                                            ExecutionContext ctx) {
                if (MigratedTo8.hasMarker(classDecl)) {
                    return classDecl;
                }

                if (classDecl.getExtends() != null) {
                    JavaType extendsType = classDecl.getExtends().getType();
                    if (!TypeUtils.isOfClassType(extendsType, REWRITE_RECIPE_FQN) &&
                        !TypeUtils.isOfClassType(extendsType, JAVA_ISO_VISITOR_FQN) &&
                        !TypeUtils.isOfClassType(extendsType, JAVA_VISITOR_FQN)
                        ) {
                        return classDecl;
                    }
                } else {
                    return classDecl;
                }

                // find `getSingleSourceApplicableTest` method
                J.MethodDeclaration singleSourceApplicableTestMethod = findSingleSourceApplicableTest(classDecl);
                if (singleSourceApplicableTestMethod != null) {
                    hasApplicableTest = true;
                    applicableTestMethodHasMultipleReturns = new JavaIsoVisitor<AtomicInteger>() {
                        @Override
                        public J.Return visitReturn(J.Return _return, AtomicInteger returnsCount) {
                            returnsCount.set(returnsCount.get() + 1);
                            return _return;
                        }
                    }.reduce(singleSourceApplicableTestMethod, new AtomicInteger(0)).get() > 1;

                    if (!applicableTestMethodHasMultipleReturns && singleSourceApplicableTestMethod.getBody() != null) {
                        List<Statement> statements = singleSourceApplicableTestMethod.getBody().getStatements();
                        applicableTestMethodStatements.addAll(statements);
                    }
                }

                // find `visit` method
                J.MethodDeclaration visitMethod = findVisitMethod(classDecl);
                if (visitMethod != null) {
                    classDecl = MigratedTo8.withMarker(classDecl);

                    return (J.ClassDeclaration) new JavaIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method,
                                                                          ExecutionContext executionContext) {
                            if (VISIT_METHOD_MATCHER.matches(method.getMethodType())) {
                                return (J.MethodDeclaration)commentOf(method, VISIT_SOURCE_FILES_COMMENT);
                            }
                            return super.visitMethodDeclaration(method, executionContext);
                        }
                    }.visitNonNull(classDecl, ctx);
                }

                return super.visitClassDeclaration(classDecl, ctx);
            }

            @SuppressWarnings("DataFlowIssue")
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method,
                                                              ExecutionContext ctx) {
                if (MigratedTo8.hasMarker(method)) {
                    return method;
                }

                if (GET_SINGLE_SOURCE_APPLICABLE_TEST_METHOD_MATCHER.matches(method.getMethodType())) {
                    if (!applicableTestMethodHasMultipleReturns)
                    {
                        // remove `org.openrewrite.Recipe getSingleSourceApplicableTest()` method
                        return null;
                    } else {
                        method = (J.MethodDeclaration) commentOf(method, COMPLEX_SINGLE_SOURCE_APPLICABLE_TEST_COMMENT);
                        method = MigratedTo8.withMarker(method);
                        return method;
                    }
                }

                if (GET_APPLICABLE_TEST_METHOD_MATCHER.matches(method.getMethodType())) {
                    // Add a comment with a link to the migration documentation to classes which use non-single source applicability tests
                    return (J.MethodDeclaration) commentOf(method, APPLICABLE_TEST_COMMENT);
                }

                if (GET_VISITOR_METHOD_MATCHER.matches(method.getMethodType())) {
                    // make the `Recipe#getVisitor()` methods public
                    if (J.Modifier.hasModifier(method.getModifiers(), Protected)) {
                        method = method.withModifiers(ListUtils.map(method.getModifiers(), mod ->
                            mod.getType() == Protected ? mod.withType(Public) : mod));
                        MigratedTo8.withMarker(method);
                    }

                    // Change return type to `TreeVisitor<?, ExecutionContext>`
                    if (method.getReturnTypeExpression() != null &&
                        !TypeUtils.isOfClassType(method.getReturnTypeExpression().getType(), "org.openrewrite.TreeVisitor")) {
                        maybeAddImport("org.openrewrite.TreeVisitor");
                        method = method.withReturnTypeExpression(getGetVisitorReturnType().withPrefix(Space.SINGLE_SPACE));
                    }

                    if (hasApplicableTest && !applicableTestMethodStatements.isEmpty()) {
                        maybeAddImport("org.openrewrite.Preconditions", false);

                        if (method.getBody() == null) {
                            return method;
                        }
                        // merge statements
                        List<Statement> getVisitorStatements = method.getBody().getStatements();
                        Statement getVisitorReturnStatements = null;
                        Statement applicableTestReturnStatement = null;
                        List<Statement> mergedStatements = new ArrayList<>();

                        for (int i = 0; i < applicableTestMethodStatements.size(); i++) {
                            if (i != applicableTestMethodStatements.size() - 1) {
                                mergedStatements.add(applicableTestMethodStatements.get(i));
                            } else {
                                applicableTestReturnStatement = applicableTestMethodStatements.get(i);
                            }
                        }

                        for (int i = 0; i < getVisitorStatements.size(); i++) {
                            if (i != getVisitorStatements.size() - 1) {
                                mergedStatements.add(getVisitorStatements.get(i));
                            } else {
                                getVisitorReturnStatements = getVisitorStatements.get(i);
                            }
                        }

                        JavaTemplate preconditionsCheckTemplate = JavaTemplate.builder(this::getCursor,
                                "return Preconditions.check(#{any()}, #{any()});")
                            .javaParser(JavaParser.fromJavaVersion()
                                .classpath(JavaParser.runtimeClasspath()))
                            .imports("org.openrewrite.Preconditions")
                            .build();

                        if (getVisitorReturnStatements == null || applicableTestReturnStatement == null) {
                            return method;
                        }

                        getVisitorReturnStatements = getVisitorReturnStatements.withTemplate(
                            preconditionsCheckTemplate, getVisitorReturnStatements.getCoordinates().replace(),
                            ((J.Return) applicableTestReturnStatement).getExpression(),
                            ((J.Return) getVisitorReturnStatements).getExpression()
                        );

                        mergedStatements.add(getVisitorReturnStatements);
                        method = method.withBody(method.getBody().withStatements(mergedStatements));
                        method = (J.MethodDeclaration) replaceApplicabilityMethods(method, ctx);
                        return MigratedTo8.withMarker( autoFormat(super.visitMethodDeclaration(method, ctx), ctx));
                    }

                    return super.visitMethodDeclaration(method, ctx);
                }

                if (VISIT_JAVA_SOURCE_FILE_METHOD_MATCHER.matches(method.getMethodType()) && method.getBody() != null) {
                    // replace with `visit` method
                    List<Statement> visitJavaSourceFileMethodStatements = method.getBody().getStatements();
                    visitJavaSourceFileMethodStatements.remove(visitJavaSourceFileMethodStatements.size() - 1);
                    J.MethodDeclaration visitMethod = buildVisitMethod(method.getParameters());
                    if (visitMethod.getBody() == null) {
                        return method;
                    }
                    List<Statement> visitMethodStatements = visitMethod.getBody().getStatements();
                    J.If ifStatement = (J.If) visitMethodStatements.get(0);
                    J.Block ifBlock =  (J.Block) ifStatement.getThenPart();

                    List<Statement> mergedIfBlockStatements = ifBlock.getStatements();
                    mergedIfBlockStatements.addAll(1, visitJavaSourceFileMethodStatements);

                    J.Block mergedIfBlock = ifBlock.withStatements(mergedIfBlockStatements);
                    J.If updatedIfStatement = ifStatement.withThenPart(mergedIfBlock);
                    visitMethod = visitMethod.withBody(visitMethod.getBody().withStatements(ListUtils.mapFirst(visitMethodStatements, a -> updatedIfStatement)));

                    // replace `.visitJavaSourceFile()` to `.visit()`
                    visitMethod = (J.MethodDeclaration) new JavaVisitor<ExecutionContext>() {
                        @Override
                        public J visitMethodInvocation(J.MethodInvocation method,
                                                                        ExecutionContext executionContext) {

                            if (VISIT_JAVA_SOURCE_FILE_METHOD_MATCHER.matches(method.getMethodType())) {
                                boolean isVariableDeclaration = getCursor().dropParentUntil(p -> p instanceof J.VariableDeclarations ||
                                    p instanceof J.Block || p instanceof J.MethodDeclaration
                                ).getValue() instanceof J.VariableDeclarations;

                                if (isVariableDeclaration) {
                                    J.TypeCast typeCast = getVisitMethodInvocationTypeCastTemplate();
                                    J.MethodInvocation exp = ((J.MethodInvocation) typeCast.getExpression()).withSelect(method.getSelect()).withArguments(method.getArguments());
                                    return typeCast.withExpression(exp);
                                } else {
                                    return getVisitMethodInvocationTemplate().withSelect(method.getSelect()).withArguments(method.getArguments());
                                }
                            }
                            return super.visitMethodInvocation(method, executionContext);
                        }

                        @Override
                        public J.MemberReference visitMemberReference(J.MemberReference memberRef,
                                                                      ExecutionContext executionContext) {
                            if (VISIT_JAVA_SOURCE_FILE_METHOD_MATCHER.matches(memberRef.getMethodType())) {
                                maybeAddImport("org.openrewrite.TreeVisitor");
                                return getVisitMemberReferenceTemplate();
                            }
                            return memberRef;
                        }
                    }.visitNonNull(visitMethod, ctx);

                    maybeAddImport("org.openrewrite.internal.lang.Nullable");
                    maybeAddImport("org.openrewrite.Tree");
                    maybeAddImport("org.openrewrite.java.tree.J");
                    return autoFormat(visitMethod, ctx);
                }

                return super.visitMethodDeclaration(method, ctx);
            }
        };
    }

    @Nullable
    private static J.MethodDeclaration findSingleSourceApplicableTest(J.ClassDeclaration classDecl) {
        return classDecl.getBody()
            .getStatements()
            .stream()
            .filter(statement -> statement instanceof J.MethodDeclaration)
            .map(J.MethodDeclaration.class::cast)
            .filter(m -> GET_SINGLE_SOURCE_APPLICABLE_TEST_METHOD_MATCHER.matches(m.getMethodType()))
            .findFirst()
            .orElse(null);
    }

    @Nullable
    private static J.MethodDeclaration findVisitMethod(J.ClassDeclaration classDecl) {
        return classDecl.getBody()
            .getStatements()
            .stream()
            .filter(statement -> statement instanceof J.MethodDeclaration)
            .map(J.MethodDeclaration.class::cast)
            .filter(m -> VISIT_METHOD_MATCHER.matches(m.getMethodType()))
            .filter(m -> m.getLeadingAnnotations().stream().anyMatch(OVERRIDE_ANNOTATION_MATCHER::matches))
            .findFirst()
            .orElse(null);
    }

    private static J replaceApplicabilityMethods(J tree, ExecutionContext ctx) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method,
                                                            ExecutionContext executionContext) {
                method = super.visitMethodInvocation(method, executionContext);
                if (APPLICABILITY_AND_METHOD_MATCHER.matches(method.getMethodType())) {
                    method = getPreconditionsAndTemplate().withArguments(method.getArguments());
                } else if (APPLICABILITY_OR_METHOD_MATCHER.matches(method.getMethodType())) {
                    method = getPreconditionsOrTemplate().withArguments(method.getArguments());
                } else if (APPLICABILITY_NOT_METHOD_MATCHER.matches(method.getMethodType())) {
                    method = getPreconditionsNotTemplate().withArguments(method.getArguments());
                }
                return method;
            }
        }.visitNonNull(tree, ctx);
    }

    private static J.MethodDeclaration buildVisitMethod(List<Statement> visitJavaSourceFileMethodParameters) {
        J.MethodDeclaration visitMethodTemplate = getVisitTreeMethodTemplate();

        J.Identifier javaSourceFiledId = ((J.VariableDeclarations)(visitJavaSourceFileMethodParameters.get(0))).getVariables().get(0).getName();
        J.Identifier secondParameter = ((J.VariableDeclarations) visitJavaSourceFileMethodParameters.get(1)).getVariables().get(0).getName();
        J.MethodDeclaration visitMethod = visitMethodTemplate.withParameters(
            ListUtils.mapLast(visitMethodTemplate.getParameters(), a -> visitJavaSourceFileMethodParameters.get(1))
        );
        return (J.MethodDeclaration) new JavaIsoVisitor<J.Identifier>(){
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, J.Identifier replacement) {
                if (TREE_VISITOR_VISIT_METHOD_MATCHER.matches(method.getMethodType())) {
                    return method.withArguments(ListUtils.mapLast(method.getArguments(),
                        a -> replacement.withPrefix(Space.SINGLE_SPACE)));
                }
                return method;
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable,
                                                                    J.Identifier identifier) {
                if (TypeUtils.isOfClassType(multiVariable.getType(), "org.openrewrite.java.tree.JavaSourceFile")) {
                    List<J.VariableDeclarations.NamedVariable> variables = multiVariable.getVariables();
                    variables = ListUtils.mapLast(variables, a -> a.withName(javaSourceFiledId));
                    return multiVariable.withVariables(variables);
                }
                return multiVariable;
            }
        }.visitNonNull(visitMethod, secondParameter);
    }

    public static J commentOf(J j, String commentText) {
        Comment comment = new TextComment(false, commentText,
            "\n" + j.getPrefix().getWhitespace().replace("\n", ""), Markers.EMPTY);
        Space prefix = j.getPrefix();
        List<Comment> comments = ListUtils.concat(prefix.getComments(), comment);
        prefix = prefix.withComments(comments);
        return j.withPrefix(prefix);
    }

    @Value
    @With
    private static class MigratedTo8 implements Marker {
        UUID id;
        static <J2 extends J> J2 withMarker(J2 j) {
            return j.withMarkers(j.getMarkers().addIfAbsent(new MigratedTo8(randomId())));
        }
        static boolean hasMarker(J j) {
            return j.getMarkers().findFirst(MigratedTo8.class).isPresent();
        }
    }

    @SuppressWarnings("all")
    private static J.ParameterizedType getGetVisitorReturnType() {
        if (getVisitorReturnTypeTemplate == null) {
            getVisitorReturnTypeTemplate = PartProvider.buildPart("import org.openrewrite.ExecutionContext;\n" +
                                                             "import org.openrewrite.TreeVisitor;\n" +
                                                             "public class A {\n" +
                                                             "    TreeVisitor<?, ExecutionContext> type;\n" +
                                                             "}", J.ParameterizedType.class, JavaParser.runtimeClasspath());
        }
        return getVisitorReturnTypeTemplate;
    }

    @SuppressWarnings("all")
    private static J.MethodDeclaration getVisitTreeMethodTemplate() {
        if (visitTreeMethodDeclarationTemplate == null) {
            //language=java
            visitTreeMethodDeclarationTemplate = PartProvider.buildPart(VISIT_TREE_METHOD_TEMPLATE_CODE,
                J.MethodDeclaration.class, JavaParser.runtimeClasspath());
        }
        return visitTreeMethodDeclarationTemplate;
    }

    @SuppressWarnings("all")
    private static J.MethodInvocation getVisitMethodInvocationTemplate() {
        if (visitTreeMethodInvocationTemplate == null) {
            //language=java
            visitTreeMethodInvocationTemplate = PartProvider.buildPart("import org.openrewrite.Tree;\n" +
                                   "import org.openrewrite.TreeVisitor;\n" +
                                   "import org.openrewrite.internal.lang.Nullable;\n" +
                                   "\n" +
                                   "public class A<T extends Tree, P> extends TreeVisitor<T, P> {\n" +
                                   "    @Override\n" +
                                   "    public @Nullable T visit(@Nullable Tree tree, P p) {\n" +
                                   "        return super.visit(tree, p);\n" +
                                   "    }\n" +
                                   "}", J.MethodInvocation.class, JavaParser.runtimeClasspath()
                );
        }
        return visitTreeMethodInvocationTemplate;
    }

    @SuppressWarnings("all")
    private static J.TypeCast getVisitMethodInvocationTypeCastTemplate () {
        if (visitTreeMethodInvocationTypeCastTemplate == null) {
            visitTreeMethodInvocationTypeCastTemplate = PartProvider.buildPart(
                "import org.openrewrite.Tree;\n" +
                "import org.openrewrite.internal.lang.Nullable;\n" +
                "import org.openrewrite.java.JavaVisitor;\n" +
                "import org.openrewrite.java.tree.JavaSourceFile;\n" +
                "public class A<P> extends JavaVisitor<P> {\n" +
                "    @Override\n" +
                "    public @Nullable JavaSourceFile visit(@Nullable Tree tree, P p) {\n" +
                "        return (JavaSourceFile) super.visit(tree, p);\n" +
                "    }\n" +
                "}",
                J.TypeCast.class, JavaParser.runtimeClasspath()
            );
        }
        return visitTreeMethodInvocationTypeCastTemplate;
    }

    @SuppressWarnings("all")
    private static J.MethodInvocation getPreconditionsAndTemplate() {
        if (preconditionAndTemplate == null) {
            preconditionAndTemplate = PartProvider.buildPart("import org.openrewrite.Preconditions;\n" +
                                                                "public class A {\n" +
                                                                "    void method() {\n" +
                                                                "         Preconditions.and(null);\n" +
                                                                "    }\n" +
                                                                "}", J.MethodInvocation.class, JavaParser.runtimeClasspath()
            );
        }
        return preconditionAndTemplate;
    }

    @SuppressWarnings("all")
    private static J.MethodInvocation getPreconditionsOrTemplate() {
        if (preconditionOrTemplate == null) {
            preconditionOrTemplate = PartProvider.buildPart("import org.openrewrite.Preconditions;\n" +
                                                                "public class A {\n" +
                                                                "    void method() {\n" +
                                                                "         Preconditions.or(null);\n" +
                                                                "    }\n" +
                                                                "}", J.MethodInvocation.class, JavaParser.runtimeClasspath()
            );
        }
        return preconditionOrTemplate;
    }

    @SuppressWarnings("all")
    private static J.MethodInvocation getPreconditionsNotTemplate() {
        if (preconditionNotTemplate == null) {
            preconditionNotTemplate = PartProvider.buildPart("import org.openrewrite.Preconditions;\n" +
                                                                "public class A {\n" +
                                                                "    void method() {\n" +
                                                                "         Preconditions.not(null);\n" +
                                                                "    }\n" +
                                                                "}", J.MethodInvocation.class, JavaParser.runtimeClasspath()
            );
        }
        return preconditionNotTemplate;
    }

    @SuppressWarnings("all")
    private static J.MemberReference getVisitMemberReferenceTemplate() {
        if (visitMemberReferenceTemplate == null) {
            visitMemberReferenceTemplate = PartProvider.buildPart(
                "import org.openrewrite.java.JavaVisitor;\n" +
                "import org.openrewrite.java.tree.J;\n" +
                "import org.openrewrite.java.tree.JavaSourceFile;\n" +
                "public class A<P> extends JavaVisitor<P> {\n" +
                "    @Override\n" +
                "    public J visitJavaSourceFile(JavaSourceFile cu, P p) {\n" +
                "        return visitAndCast(cu, p, super::visit);\n" +
                "    }\n" +
                "}", J.MemberReference.class,
                JavaParser.runtimeClasspath()
                );
        }

        return visitMemberReferenceTemplate;
    }
}
