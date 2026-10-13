package com.dataguard.analyzer;

import com.dataguard.entity.Finding;
import com.dataguard.entity.Review;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Language-aware syntax checks backed by tree-sitter-language-pack (MIT OR Apache-2.0).
 * The parser detects syntax-tree errors; it does not claim type-checking or compilation.
 */
@Component
public class SyntaxAnalyzer {
    private static final Set<String> IGNORED_DIRS = Set.of(
            ".git", "node_modules", "target", "build", "dist", ".next", "vendor",
            "__pycache__", ".venv", "venv", "coverage");
    private static final String PARSER_SCRIPT = """
            import json, os, sys
            from tree_sitter_language_pack import get_parser

            root = os.path.abspath(sys.argv[1])
            extensions = {
              ".java":"java", ".js":"javascript", ".jsx":"javascript", ".mjs":"javascript",
              ".cjs":"javascript", ".ts":"typescript", ".tsx":"tsx", ".py":"python",
              ".go":"go", ".rs":"rust", ".c":"c", ".h":"c", ".cc":"cpp", ".cpp":"cpp",
              ".cxx":"cpp", ".hpp":"cpp", ".cs":"c_sharp", ".php":"php", ".rb":"ruby",
              ".swift":"swift", ".kt":"kotlin", ".kts":"kotlin", ".scala":"scala",
              ".sh":"bash", ".bash":"bash", ".sql":"sql", ".html":"html", ".htm":"html",
              ".css":"css", ".scss":"scss", ".vue":"vue", ".svelte":"svelte",
              ".json":"json", ".yaml":"yaml", ".yml":"yaml", ".toml":"toml",
              ".xml":"xml", ".md":"markdown", ".lua":"lua", ".dart":"dart",
              ".ex":"elixir", ".exs":"elixir", ".erl":"erlang", ".hs":"haskell",
              ".pl":"perl", ".r":"r", ".jl":"julia", ".m":"objc", ".mm":"objc"
            }
            ignored = {".git", "node_modules", "target", "build", "dist", ".next",
                       "vendor", "__pycache__", ".venv", "venv", "coverage"}
            results = []
            checked = 0
            max_files = 3000
            max_bytes = 1024 * 1024
            for current, dirs, files in os.walk(root):
                dirs[:] = [d for d in dirs if d not in ignored]
                for name in files:
                    ext = os.path.splitext(name)[1].lower()
                    language = extensions.get(ext)
                    if not language:
                        continue
                    path = os.path.join(current, name)
                    try:
                        if os.path.getsize(path) > max_bytes:
                            continue
                        with open(path, "rb") as source:
                            code = source.read()
                        if b"\\x00" in code:
                            continue
                        parser = get_parser(language)
                        tree = parser.parse(code)
                        checked += 1
                        stack = [tree.root_node]
                        file_errors = 0
                        while stack and file_errors < 5:
                            node = stack.pop()
                            is_missing = getattr(node, "is_missing", False)
                            if node.type == "ERROR" or is_missing:
                                row = node.start_point[0] + 1
                                results.append({
                                    "path": os.path.relpath(path, root).replace(os.sep, "/"),
                                    "line": row,
                                    "language": language,
                                    "node": "missing syntax token" if is_missing else "invalid syntax",
                                    "excerpt": code.splitlines()[row-1][:240].decode("utf-8", "replace")
                                               if row <= len(code.splitlines()) else ""
                                })
                                file_errors += 1
                            else:
                                stack.extend(reversed(node.children))
                    except Exception:
                        # Unsupported grammar or unreadable source is not mislabeled as a syntax error.
                        continue
                    if checked >= max_files:
                        break
                if checked >= max_files:
                    break
            print(json.dumps({"checked": checked, "findings": results}))
            """;

    @Value("${dataguard.syntax.python-executable:python3}")
    private String pythonExecutable = "python3";

    @Value("${dataguard.syntax.timeout-seconds:90}")
    private long timeoutSeconds = 90;

    private final ObjectMapper objectMapper;

    public SyntaxAnalyzer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<Finding> analyze(File projectDirectory, Review review) {
        if (projectDirectory == null || !projectDirectory.isDirectory()) {
            throw new IllegalArgumentException("Syntax analysis requires an existing project directory.");
        }
        if (timeoutSeconds <= 0) {
            throw new IllegalStateException("DATAGUARD_SYNTAX_TIMEOUT_SECONDS must be greater than zero.");
        }

        Path output = null;
        Process process = null;
        try {
            output = Files.createTempFile("dataguard-syntax-", ".json");
            process = new ProcessBuilder(pythonExecutable, "-c", PARSER_SCRIPT, projectDirectory.getAbsolutePath())
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .redirectOutput(output.toFile())
                    .start();
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("Syntax analysis exceeded its configured timeout.");
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("Syntax parser failed. Ensure tree-sitter-language-pack is installed for the configured Python executable.");
            }
            JsonNode root = objectMapper.readTree(Files.readString(output, StandardCharsets.UTF_8));
            JsonNode results = root.path("findings");
            if (!results.isArray()) {
                throw new IllegalStateException("Syntax parser returned an invalid result.");
            }

            List<Finding> findings = new ArrayList<>();
            for (JsonNode result : results) {
                String path = result.path("path").asText("");
                int line = Math.max(1, result.path("line").asInt(1));
                String language = result.path("language").asText("source");
                String excerpt = result.path("excerpt").asText("");
                Finding finding = new Finding();
                finding.setReview(review);
                finding.setCategory("SYNTAX");
                finding.setSeverity("HIGH");
                finding.setTitle("Syntax error (" + language + ")");
                finding.setFilePath(path);
                finding.setLineNumber(line);
                finding.setEvidence((result.path("node").asText("invalid syntax") + (excerpt.isBlank() ? "" : ": " + excerpt))
                        .substring(0, Math.min(1800, (result.path("node").asText("invalid syntax") + (excerpt.isBlank() ? "" : ": " + excerpt)).length())));
                finding.setRecommendation("Review the reported line and correct the syntax before relying on this file. This parser checks syntax, not types or runtime behavior.");
                finding.setStatus("OPEN");
                findings.add(finding);
            }
            return findings;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Syntax analysis was interrupted.", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not run the syntax parser. Ensure Python and tree-sitter-language-pack are installed.", exception);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            if (output != null) {
                try {
                    Files.deleteIfExists(output);
                } catch (IOException ignored) {
                    // Temporary-file cleanup must not mask the review result.
                }
            }
        }
    }
}
