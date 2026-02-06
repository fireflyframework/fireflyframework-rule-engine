package org.fireflyframework.rules.core.dsl.compiler;

import org.fireflyframework.rules.core.dsl.model.ASTRulesDSL;
import org.fireflyframework.rules.core.dsl.parser.ASTRulesDSLParser;
import org.fireflyframework.rules.core.validation.YamlDslValidator;
import org.fireflyframework.rules.interfaces.dtos.validation.ValidationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for compiling YAML DSL rules to Python code.
 *
 * This service provides functionality to:
 * - Parse YAML DSL to AST
 * - Validate the AST
 * - Compile AST to Python code
 * - Cache compiled rules
 * - Manage compilation metadata
 *
 * @author Firefly Software Solutions Inc
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PythonCompilationService {

    private final ASTRulesDSLParser astRulesDSLParser;
    private final YamlDslValidator yamlDslValidator;
    private final PythonCodeGenerator pythonCodeGenerator;

    // Cache for compiled rules
    private final Map<String, PythonCompiledRule> compilationCache = new ConcurrentHashMap<>();

    // Compilation statistics
    private long totalCompilations = 0;
    private long successfulCompilations = 0;
    private long failedCompilations = 0;
    private long cacheHits = 0;

    /**
     * Compile a YAML DSL rule to Python code.
     *
     * @param yamlDsl The YAML DSL rule definition
     * @param ruleName Optional rule name (will be extracted from DSL if not provided)
     * @param useCache Whether to use compilation cache
     * @return Compiled Python rule
     * @throws PythonCompilationException if compilation fails
     */
    public PythonCompiledRule compileRule(String yamlDsl, String ruleName, boolean useCache) {
        log.debug("Starting Python compilation for rule: {}", ruleName);

        totalCompilations++;

        try {
            // Generate cache key
            String cacheKey = generateCacheKey(yamlDsl, ruleName);

            // Check cache if enabled
            if (useCache && compilationCache.containsKey(cacheKey)) {
                log.debug("Cache hit for rule: {}", ruleName);
                cacheHits++;
                return compilationCache.get(cacheKey);
            }

            // Parse YAML DSL to AST
            ASTRulesDSL astRule = astRulesDSLParser.parseRules(yamlDsl);

            // Validate the AST
            ValidationResult validationResult = yamlDslValidator.validate(yamlDsl);
            if (!validationResult.isValid()) {
                throw new PythonCompilationException(
                    "DSL validation failed: " + validationResult.getErrors()
                );
            }

            // Generate Python code
            String finalRuleName = ruleName != null ? ruleName :
                (astRule.getName() != null ? astRule.getName() : "unnamed_rule");
            String pythonCode = pythonCodeGenerator.generatePythonFunction(astRule, finalRuleName);

            // Create compiled rule
            PythonCompiledRule compiledRule = createCompiledRule(astRule, pythonCode, ruleName);

            // Cache the result if enabled
            if (useCache) {
                compilationCache.put(cacheKey, compiledRule);
            }

            successfulCompilations++;
            log.info("Successfully compiled rule '{}' to Python", compiledRule.getRuleName());

            return compiledRule;

        } catch (Exception e) {
            failedCompilations++;
            log.error("Failed to compile rule '{}' to Python: {}", ruleName, e.getMessage(), e);
            throw new PythonCompilationException("Compilation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Compile a YAML DSL rule to Python code with default settings.
     *
     * @param yamlDsl The YAML DSL rule definition
     * @return Compiled Python rule
     */
    public PythonCompiledRule compileRule(String yamlDsl) {
        return compileRule(yamlDsl, null, true);
    }

    /**
     * Compile a YAML DSL rule to Python code with specified rule name.
     *
     * @param yamlDsl The YAML DSL rule definition
     * @param ruleName The rule name
     * @return Compiled Python rule
     */
    public PythonCompiledRule compileRule(String yamlDsl, String ruleName) {
        return compileRule(yamlDsl, ruleName, true);
    }

    /**
     * Batch compile multiple YAML DSL rules to Python code.
     *
     * @param rules Map of rule names to YAML DSL definitions
     * @param useCache Whether to use compilation cache
     * @return Map of rule names to compiled Python rules
     */
    public Map<String, PythonCompiledRule> compileRules(Map<String, String> rules, boolean useCache) {
        log.info("Starting batch compilation of {} rules", rules.size());

        Map<String, PythonCompiledRule> compiledRules = new ConcurrentHashMap<>();

        rules.entrySet().parallelStream().forEach(entry -> {
            try {
                PythonCompiledRule compiledRule = compileRule(entry.getValue(), entry.getKey(), useCache);
                compiledRules.put(entry.getKey(), compiledRule);
            } catch (Exception e) {
                log.error("Failed to compile rule '{}': {}", entry.getKey(), e.getMessage());
                // Continue with other rules
            }
        });

        log.info("Batch compilation completed. Successfully compiled {}/{} rules",
                compiledRules.size(), rules.size());

        return compiledRules;
    }

    /**
     * Clear the compilation cache.
     */
    public void clearCache() {
        compilationCache.clear();
        log.info("Compilation cache cleared");
    }

    /**
     * Get compilation statistics.
     *
     * @return Map containing compilation statistics
     */
    public Map<String, Object> getCompilationStats() {
        return Map.of(
            "totalCompilations", totalCompilations,
            "successfulCompilations", successfulCompilations,
            "failedCompilations", failedCompilations,
            "cacheHits", cacheHits,
            "cacheSize", compilationCache.size(),
            "successRate", totalCompilations > 0 ? (double) successfulCompilations / totalCompilations : 0.0,
            "cacheHitRate", totalCompilations > 0 ? (double) cacheHits / totalCompilations : 0.0
        );
    }

    /**
     * Get cached rule by cache key.
     *
     * @param yamlDsl The YAML DSL
     * @param ruleName The rule name
     * @return Cached compiled rule or null if not found
     */
    public PythonCompiledRule getCachedRule(String yamlDsl, String ruleName) {
        String cacheKey = generateCacheKey(yamlDsl, ruleName);
        return compilationCache.get(cacheKey);
    }

    /**
     * Check if a rule is cached.
     *
     * @param yamlDsl The YAML DSL
     * @param ruleName The rule name
     * @return True if the rule is cached
     */
    public boolean isRuleCached(String yamlDsl, String ruleName) {
        String cacheKey = generateCacheKey(yamlDsl, ruleName);
        return compilationCache.containsKey(cacheKey);
    }

    /**
     * Remove a specific rule from cache.
     *
     * @param yamlDsl The YAML DSL
     * @param ruleName The rule name
     * @return True if the rule was removed from cache
     */
    public boolean removeCachedRule(String yamlDsl, String ruleName) {
        String cacheKey = generateCacheKey(yamlDsl, ruleName);
        return compilationCache.remove(cacheKey) != null;
    }

    /**
     * Remove cached rules by rule name pattern.
     * This method removes all cached rules that match the given rule name.
     * Useful for DELETE endpoints that should not have request bodies.
     *
     * @param ruleName The rule name to remove from cache
     * @return True if at least one rule was removed from cache
     */
    public boolean removeCachedRuleByName(String ruleName) {
        boolean removed = false;

        // Find all cache keys that start with the rule name
        for (String cacheKey : compilationCache.keySet()) {
            if (cacheKey.startsWith(ruleName + "_")) {
                if (compilationCache.remove(cacheKey) != null) {
                    removed = true;
                    log.debug("Removed cached rule with key: {}", cacheKey);
                }
            }
        }

        if (removed) {
            log.info("Removed cached rules for rule name: {}", ruleName);
        } else {
            log.debug("No cached rules found for rule name: {}", ruleName);
        }

        return removed;
    }

    // Private helper methods

    private String generateCacheKey(String yamlDsl, String ruleName) {
        // Use a combination of rule name and DSL hash for cache key
        int dslHash = yamlDsl.hashCode();
        String name = ruleName != null ? ruleName : "unnamed";
        return name + "_" + dslHash;
    }

    private PythonCompiledRule createCompiledRule(ASTRulesDSL astRule, String pythonCode, String ruleName) {
        String finalRuleName = ruleName != null ? ruleName :
            (astRule.getName() != null ? astRule.getName() : "unnamed_rule");

        // Use description if available, otherwise use name
        String description = astRule.getDescription() != null && !astRule.getDescription().trim().isEmpty()
            ? astRule.getDescription()
            : astRule.getName();

        return PythonCompiledRule.builder()
            .ruleName(finalRuleName)
            .description(description)
            .version(astRule.getVersion() != null ? astRule.getVersion() : "1.0.0")
            .pythonCode(pythonCode)
            .functionName(pythonCodeGenerator.sanitizeFunctionName(finalRuleName))
            .inputVariables(pythonCodeGenerator.extractInputVariables(astRule))
            .outputVariables(pythonCodeGenerator.extractOutputVariables(astRule))
            .compiledAt(LocalDateTime.now())
            .sourceHash(String.valueOf(astRule.hashCode()))
            .build();
    }

    /**
     * Exception thrown when Python compilation fails.
     */
    public static class PythonCompilationException extends RuntimeException {
        public PythonCompilationException(String message) {
            super(message);
        }

        public PythonCompilationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
