/*
 * Copyright 2025 Firefly Software Solutions Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.firefly.rules.core.dsl.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * Represents a constant definition in the rule DSL.
 * Constants are fixed values that can be referenced throughout the rule.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConstantDefinition {

    /**
     * The name of the constant
     */
    private String name;

    /**
     * The value of the constant
     */
    private Object value;

    /**
     * The data type of the constant (string, number, boolean, date, object)
     */
    private String type;

    /**
     * Description of what this constant represents
     */
    private String description;
}
