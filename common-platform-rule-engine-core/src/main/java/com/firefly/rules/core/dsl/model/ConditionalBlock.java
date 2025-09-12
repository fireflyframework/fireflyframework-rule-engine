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
 * Represents a conditional block (if/then/else) in the rule DSL.
 * This is the core logic structure that defines rule behavior.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConditionalBlock {

    /**
     * The condition to evaluate
     */
    @JsonProperty("if")
    private Condition ifCondition;

    /**
     * Actions to execute if the condition is true
     */
    @JsonProperty("then")
    private ActionBlock thenBlock;

    /**
     * Actions to execute if the condition is false (optional)
     */
    @JsonProperty("else")
    private ActionBlock elseBlock;
}
