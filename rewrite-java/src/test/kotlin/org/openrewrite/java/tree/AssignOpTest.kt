/*
 * Copyright 2020 the original author or authors.
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
package org.openrewrite.java.tree

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.openrewrite.java.JavaParser
import org.openrewrite.java.firstMethodStatement

interface AssignOpTest {
    @Test
    fun compoundAssignment(jp: JavaParser) {
        val a = jp.parse("""
            public class A {
                int n = 0;
                public void test() {
                    n += 1;
                }
            }
        """)

        val assign = a.firstMethodStatement() as J.AssignOp

        assertTrue(assign.operator is J.AssignOp.Operator.Addition)
        assertEquals("n += 1", assign.printTrimmed())
    }
}
