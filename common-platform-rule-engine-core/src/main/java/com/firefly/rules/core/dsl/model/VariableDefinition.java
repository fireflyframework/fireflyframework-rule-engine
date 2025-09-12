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

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * Represents a variable definition in the rule DSL.
 * Variables are input parameters that the rule expects to receive during evaluation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VariableDefinition {
 
    /**
     * The name of the variable
     */
    private String name;

    /**
     * The data type of the variable (string, number, boolean, date, object, array)
     */
    private String type;

    /**
     * Whether this variable is required for rule evaluation
     */
    private Boolean required;

    /**
     * Default value if the variable is not provided
     */
    @JsonProperty("default")
    private Object defaultValue;

    /**
     * Description of what this variable represents
     */
    private String description;

    /**
     * Reference to a system variable (from Variable entity) by code
     * If specified, this rule variable maps to a system variable
     */
    @JsonProperty("system_variable")
    private String systemVariableCode;

    /**
     * Validation rules for the variable
     */
    private VariableValidation validation;

    /**
     * Validation configuration for variables
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariableValidation {
        private Object min;
        private Object max;
        private String pattern;
        private java.util.List<Object> allowedValues;
    }
}
