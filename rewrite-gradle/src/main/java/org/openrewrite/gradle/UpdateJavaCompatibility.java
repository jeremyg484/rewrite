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
package org.openrewrite.gradle;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.gradle.util.ChangeStringLiteral;
import org.openrewrite.groovy.GroovyVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;

import java.util.Collections;
import java.util.List;

import static org.openrewrite.Tree.randomId;

@Value
@EqualsAndHashCode(callSuper = true)
public class UpdateJavaCompatibility extends Recipe {
    @Option(displayName = "Java version",
            description = "The Java version to upgrade to.",
            example = "11")
    Integer version;

    @Option(displayName = "Compatibility Type",
            description = "The compatibility type to change",
            valid = {"source", "target"},
            required = false)
    @Nullable
    CompatibilityType compatibilityType;

    @Option(displayName = "Declaration Style",
            description = "The desired style to write the new version as when being written to the `sourceCompatibility` " +
                    "or `targetCompatibility` variables. Default, match current source style. " +
                    "(ex. Enum: `JavaVersion.VERSION_11`, Number: 11, or String: \"11\")",
            valid = {"Enum", "Number", "String"},
            required = false)
    @Nullable
    DeclarationStyle declarationStyle;

    @Override
    public String getDisplayName() {
        return "Update Gradle project Java compatibility";
    }

    @Override
    public String getDescription() {
        return "Find and updates the Java compatibility for the Gradle project.";
    }

    @Override
    public Validated validate() {
        return super.validate().and(Validated.test("version", "Version must be > 0.", version, v -> v > 0));
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new IsBuildGradle<>(), new GroovyVisitor<ExecutionContext>() {
            final MethodMatcher sourceCompatibilityDsl = new MethodMatcher("RewriteGradleProject setSourceCompatibility(..)");
            final MethodMatcher targetCompatibilityDsl = new MethodMatcher("RewriteGradleProject setTargetCompatibility(..)");
            final MethodMatcher javaLanguageVersionMatcher = new MethodMatcher("org.gradle.jvm.toolchain.JavaLanguageVersion of(int)");

            @Override
            public J visitAssignment(J.Assignment assignment, ExecutionContext executionContext) {
                J.Assignment a = (J.Assignment) super.visitAssignment(assignment, executionContext);

                if (a.getVariable() instanceof J.Identifier) {
                    J.Identifier var = (J.Identifier) a.getVariable();
                    if (compatibilityType != null && !(compatibilityType.toString().toLowerCase() + "Compatibility").equals(var.getSimpleName())) {
                        return a;
                    }
                } else if (a.getVariable() instanceof J.FieldAccess) {
                    J.FieldAccess fieldAccess = (J.FieldAccess) a.getVariable();
                    if (compatibilityType != null && !(compatibilityType.toString().toLowerCase() + "Compatibility").equals(fieldAccess.getSimpleName())) {
                        return a;
                    }
                } else {
                    return a;
                }

                DeclarationStyle currentStyle = getCurrentStyle(a.getAssignment());
                int currentMajor = getMajorVersion(a.getAssignment());
                if (currentMajor != version || (declarationStyle != null && declarationStyle != currentStyle)) {
                    DeclarationStyle actualStyle = declarationStyle == null ? currentStyle : declarationStyle;
                    if (actualStyle != null) {
                        return a.withAssignment(changeExpression(a.getAssignment(), actualStyle));
                    }
                }

                return a;
            }

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (javaLanguageVersionMatcher.matches(m)) {
                    List<Expression> args = m.getArguments();

                    if (args.size() == 1 && args.get(0) instanceof J.Literal) {
                        J.Literal versionArg = (J.Literal) args.get(0);
                        if (versionArg.getValue() instanceof Integer) {
                            Integer versionNumber = (Integer) versionArg.getValue();
                            if (!version.equals(versionNumber)) {
                                return m.withArguments(
                                        Collections.singletonList(versionArg.withValue(version)
                                                .withValueSource(version.toString())));
                            } else {
                                return m;
                            }
                        }
                    }

                    return SearchResult.found(m, "Attempted to update to Java version to " + version
                            + "  but was unsuccessful, please update manually");
                }

                if (sourceCompatibilityDsl.matches(m) || targetCompatibilityDsl.matches(m)) {
                    if (compatibilityType != null && (
                            (compatibilityType == CompatibilityType.source && !sourceCompatibilityDsl.matches(m)) ||
                                    (compatibilityType == CompatibilityType.target && !targetCompatibilityDsl.matches(m)))) {
                        return m;
                    }

                    if (m.getArguments().size() == 1 && (m.getArguments().get(0) instanceof J.Literal || m.getArguments().get(0) instanceof J.FieldAccess)) {
                        DeclarationStyle currentStyle = getCurrentStyle(m.getArguments().get(0));
                        int currentMajor = getMajorVersion(m.getArguments().get(0));
                        if (currentMajor != version || (declarationStyle != null && declarationStyle != currentStyle)) {
                            DeclarationStyle actualStyle = declarationStyle == null ? currentStyle : declarationStyle;
                            return m.withArguments(ListUtils.mapFirst(m.getArguments(), arg -> changeExpression(arg, actualStyle)));
                        } else {
                            return m;
                        }
                    }

                    return SearchResult.found(m, "Attempted to update to Java version to " + version
                            + "  but was unsuccessful, please update manually");
                }

                return m;
            }

            private int getMajorVersion(String version) {
                try {
                    return Integer.parseInt(normalize(version));
                } catch (NumberFormatException e) {
                    return -1;
                }
            }

            private int getMajorVersion(Expression expression) {
                if (expression instanceof J.Literal) {
                    J.Literal argument = (J.Literal) expression;
                    JavaType.Primitive type = argument.getType();
                    if (type == JavaType.Primitive.String) {
                        return getMajorVersion((String) argument.getValue());
                    } else if (type == JavaType.Primitive.Int) {
                        return (int) argument.getValue();
                    } else if (type == JavaType.Primitive.Double) {
                        return getMajorVersion(argument.getValue().toString());
                    }
                } else if (expression instanceof J.FieldAccess) {
                    J.FieldAccess field = (J.FieldAccess) expression;
                    J.Identifier identifier = field.getName();
                    return getMajorVersion(identifier.getSimpleName());
                }

                return -1;
            }

            private @Nullable DeclarationStyle getCurrentStyle(Expression expression) {
                if (expression instanceof J.Literal) {
                    J.Literal argument = (J.Literal) expression;
                    JavaType.Primitive type = argument.getType();
                    if (type == JavaType.Primitive.String) {
                        return DeclarationStyle.String;
                    } else if (type == JavaType.Primitive.Int) {
                        return DeclarationStyle.Number;
                    } else if (type == JavaType.Primitive.Double) {
                        return DeclarationStyle.Number;
                    }
                } else if (expression instanceof J.FieldAccess) {
                    return DeclarationStyle.Enum;
                }

                return null;
            }

            private String normalize(String version) {
                if (version.contains("\"") || version.contains("'")) {
                    version = version.replace("\"", "").replace("'", "");
                }

                if (!version.contains(".") && !version.contains("_")) {
                    return version;
                }

                if (version.contains("_")) {
                    String removePrefix = version.substring(version.indexOf("_") + 1);
                    if (removePrefix.startsWith("1_")) {
                        return removePrefix.substring(removePrefix.indexOf("_") + 1);
                    } else {
                        return removePrefix;
                    }
                } else {
                    return version.substring(version.indexOf(".") + 1);
                }
            }

            private Expression changeExpression(Expression expression, DeclarationStyle style) {
                if (expression instanceof J.Literal) {
                    J.Literal literal = (J.Literal) expression;
                    if (style == DeclarationStyle.String) {
                        String newVersion = version <= 8 ? "1." + version : String.valueOf(version);
                        if (literal.getType() == JavaType.Primitive.String) {
                            expression = ChangeStringLiteral.withStringValue(literal, newVersion);
                        } else {
                            expression = literal.withType(JavaType.Primitive.String).withValue(newVersion).withValueSource("'" + newVersion + "'");
                        }
                    } else if (style == DeclarationStyle.Enum) {
                        String name = version <= 8 ? "VERSION_1_" + version : "VERSION_" + version;
                        expression = new J.FieldAccess(
                                randomId(),
                                literal.getPrefix(),
                                literal.getMarkers(),
                                new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, "JavaVersion", JavaType.ShallowClass.build("org.gradle.api.JavaVersion"), null),
                                new JLeftPadded<>(Space.EMPTY, new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, name, null, null), Markers.EMPTY),
                                JavaType.ShallowClass.build("org.gradle.api.JavaVersion")
                        );
                    } else if (style == DeclarationStyle.Number) {
                        if (version <= 8) {
                            double doubleValue = Double.parseDouble("1." + version);
                            expression = literal.withType(JavaType.Primitive.Double).withValue(doubleValue).withValueSource("1." + version);
                        } else {
                            expression = literal.withType(JavaType.Primitive.Int).withValue(version).withValueSource(String.valueOf(version));
                        }
                    }
                } else if (expression instanceof J.FieldAccess) {
                    J.FieldAccess fieldAccess = (J.FieldAccess) expression;
                    if (style == DeclarationStyle.String) {
                        String newVersion = version <= 8 ? "1." + version : String.valueOf(version);
                        expression = new J.Literal(randomId(), fieldAccess.getPrefix(), fieldAccess.getMarkers(), newVersion, "'" + newVersion + "'", Collections.emptyList(), JavaType.Primitive.String);
                    } else if (style == DeclarationStyle.Enum) {
                        String name = version <= 8 ? "VERSION_1_" + version : "VERSION_" + version;
                        expression = fieldAccess.withName(fieldAccess.getName().withSimpleName(name));
                    } else if (style == DeclarationStyle.Number) {
                        if (version <= 8) {
                            double doubleValue = Double.parseDouble("1." + version);
                            expression = new J.Literal(randomId(), fieldAccess.getPrefix(), fieldAccess.getMarkers(), doubleValue, String.valueOf(doubleValue), Collections.emptyList(), JavaType.Primitive.Double);
                        } else {
                            expression = new J.Literal(randomId(), fieldAccess.getPrefix(), fieldAccess.getMarkers(), version, String.valueOf(version), Collections.emptyList(), JavaType.Primitive.Int);
                        }
                    }
                }

                return expression;
            }
        });
    }

    public enum CompatibilityType {
        source, target
    }

    public enum DeclarationStyle {
        Enum, Number, String
    }
}