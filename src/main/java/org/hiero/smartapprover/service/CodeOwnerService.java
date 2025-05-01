package org.hiero.smartapprover.service;

import org.hiero.smartapprover.model.CodeOwnerRule;
import io.github.azagniotov.matcher.AntPathMatcher;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for parsing and handling CODEOWNERS files
 */
@Service
public class CodeOwnerService {
    private static final Logger logger = LoggerFactory.getLogger(CodeOwnerService.class);
    private static final String[] CODEOWNERS_PATHS = {
            ".github/CODEOWNERS",
            "docs/CODEOWNERS",
            ".gitlab/CODEOWNERS",
            "CODEOWNERS"
    };

    private final GitHubService gitHubService;
    private final AntPathMatcher pathMatcher;

    // Cache of code owner rules by repo and ref to avoid reloading for every event
    private final Map<String, Map<String, List<CodeOwnerRule>>> codeOwnerRulesCache = new ConcurrentHashMap<>();

    public CodeOwnerService(GitHubService gitHubService) {
        this.gitHubService = gitHubService;
        this.pathMatcher = new AntPathMatcher.Builder().build();
    }

    /**
     * Fetches code owner rules for a specific repository and reference (branch/tag)
     * Uses cache when available to improve performance
     *
     * @param repository The GitHub repository
     * @param ref The reference (branch or tag)
     * @return List of code owner rules
     */
    public List<CodeOwnerRule> getCodeOwnerRules(GHRepository repository, String ref) {
        String cacheKey = repository.getFullName() + ":" + ref;

        // Check cache first
        if (codeOwnerRulesCache.containsKey(cacheKey) && codeOwnerRulesCache.get(cacheKey).containsKey(ref)) {
            logger.debug("Using cached CODEOWNERS rules for {} at {}", repository.getFullName(), ref);
            return codeOwnerRulesCache.get(cacheKey).get(ref);
        }

        // Try to find the CODEOWNERS file in various locations
        for (String path : CODEOWNERS_PATHS) {
            Optional<GHContent> codeOwnersFile = gitHubService.getFileContent(repository, path, ref);

            if (codeOwnersFile.isPresent()) {
                try {
                    String content = new String(Base64.getDecoder().decode(codeOwnersFile.get().getContent()));
                    List<CodeOwnerRule> rules = parseCodeOwnersFile(content);

                    // Cache the results
                    codeOwnerRulesCache.computeIfAbsent(cacheKey, k -> new ConcurrentHashMap<>())
                            .put(ref, rules);

                    return rules;
                } catch (IOException e) {
                    logger.error("Failed to read CODEOWNERS file: {}", e.getMessage());
                }
            }
        }

        logger.info("No CODEOWNERS file found in repository {}", repository.getFullName());
        return Collections.emptyList();
    }

    /**
     * Parses a CODEOWNERS file content into a list of CodeOwnerRule objects
     *
     * @param content The content of the CODEOWNERS file
     * @return List of parsed code owner rules
     */
    public List<CodeOwnerRule> parseCodeOwnersFile(String content) {
        List<CodeOwnerRule> rules = new ArrayList<>();
        Pattern ownersPattern = Pattern.compile("^([^#\\s].+?)\\s+(@[\\w-]+|@\\[[\\w-]+/[\\w-]+\\]|[\\w.-]+@[\\w.-]+)(?:\\s+(@[\\w-]+|@\\[[\\w-]+/[\\w-]+\\]|[\\w.-]+@[\\w.-]+))*\\s*$");

        try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();

                // Skip comments and empty lines
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                Matcher matcher = ownersPattern.matcher(line);
                if (matcher.matches()) {
                    String pattern = matcher.group(1).trim();

                    // Extract all owners from the line
                    List<String> owners = new ArrayList<>();
                    for (int i = 2; i <= matcher.groupCount(); i++) {
                        String owner = matcher.group(i);
                        if (owner != null) {
                            owners.add(owner.trim());
                        }
                    }

                    if (!owners.isEmpty()) {
                        rules.add(new CodeOwnerRule(pattern, owners));
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Error parsing CODEOWNERS file: {}", e.getMessage());
        }

        return rules;
    }

    /**
     * Find code owners for a specific file path
     *
     * @param rules List of code owner rules
     * @param filePath Path of the file to check
     * @return Set of owners for the file
     */
    public Set<String> getOwnersForFile(List<CodeOwnerRule> rules, String filePath) {
        // Process rules in reverse order (last matching pattern takes precedence)
        List<CodeOwnerRule> reversedRules = new ArrayList<>(rules);
        Collections.reverse(reversedRules);

        for (CodeOwnerRule rule : reversedRules) {
            String pattern = rule.pattern();

            // Convert GitHub's pattern to Ant-style pattern
            String antPattern = pattern
                    .replace("**/", "**")  // GitHub uses **/ for any directory depth
                    .replace("/**/", "/**"); // Handle middle-path matches

            // Handle file extension patterns
            if (pattern.startsWith("*.")) {
                if (filePath.endsWith(pattern.substring(1))) {
                    return new HashSet<>(rule.owners());
                }
            }
            // Handle directory-specific patterns
            else if (pathMatcher.isMatch(antPattern, filePath)) {
                return new HashSet<>(rule.owners());
            }
        }

        return Collections.emptySet();
    }

    /**
     * Map changed files to their owners
     *
     * @param rules List of code owner rules
     * @param changedFiles Set of files that have changed
     * @return Map of files to their respective owners
     */
    public Map<String, Set<String>> mapFilesToOwners(List<CodeOwnerRule> rules, Set<String> changedFiles) {
        Map<String, Set<String>> fileOwners = new HashMap<>();

        for (String file : changedFiles) {
            Set<String> owners = getOwnersForFile(rules, file);
            fileOwners.put(file, owners);
        }

        return fileOwners;
    }

    /**
     * Get all owners affected by the changed files
     *
     * @param fileOwners Map of files to their owners
     * @return Set of all affected owners
     */
    public Set<String> getAffectedOwners(Map<String, Set<String>> fileOwners) {
        return fileOwners.values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
    }

    /**
     * Clear the cache for a specific repository and reference
     *
     * @param repoFullName Full name of the repository
     * @param ref Reference (branch or tag)
     */
    public void clearCache(String repoFullName, String ref) {
        String cacheKey = repoFullName + ":" + ref;
        if (codeOwnerRulesCache.containsKey(cacheKey)) {
            if (ref != null) {
                codeOwnerRulesCache.get(cacheKey).remove(ref);
                logger.debug("Cleared cache for repository {} and ref {}", repoFullName, ref);
            } else {
                codeOwnerRulesCache.remove(cacheKey);
                logger.debug("Cleared cache for repository {}", repoFullName);
            }
        }
    }

    /**
     * Clear the entire cache
     */
    public void clearAllCache() {
        codeOwnerRulesCache.clear();
        logger.info("Cleared all CODEOWNERS cache");
    }
}
