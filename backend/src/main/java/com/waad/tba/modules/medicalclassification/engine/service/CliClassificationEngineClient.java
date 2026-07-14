package com.waad.tba.modules.medicalclassification.engine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.waad.tba.modules.medicalclassification.engine.dto.ClassificationRequest;
import com.waad.tba.modules.medicalclassification.engine.dto.ClassificationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Phase-1 engine transport (A1): Spring Boot → ProcessBuilder →
 * classify_json.py (beside the untouched authoritative script, A9) → JSON.
 *
 * Serialized on purpose: one classification process at a time (imports are
 * queued by the orchestration layer; this is a second, defensive gate).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CliClassificationEngineClient implements ClassificationEngineClient {

    private static final String ENTRY_POINT = "classify_json.py";
    private static final List<String> REQUIRED_ENGINE_FILES = List.of(
            ENTRY_POINT,
            "tpa_service_mapper.py",
            "ingest.py",
            "official_taxonomy.json",
            "official_knowledge.json",
            "medical_synonyms.json");
    private static final Object PROCESS_LOCK = new Object();

    private final ClassificationSettingsService settings;
    private final ObjectMapper objectMapper;

    @Override
    public ClassificationResult classify(ClassificationRequest request) {
        Path scriptDir = requireScriptDir();
        String python = resolvePython(scriptDir);
        int timeoutSeconds = settings.getInt(ClassificationSettingsService.KEY_TIMEOUT_SECONDS, 600);

        synchronized (PROCESS_LOCK) {
            Path workDir = null;
            try {
                workDir = Files.createTempDirectory("mce-run-");
                Path requestFile = workDir.resolve("request.json");
                Path resultFile = workDir.resolve("result.json");
                objectMapper.writeValue(requestFile.toFile(), toEngineJson(request));

                List<String> command = new ArrayList<>(List.of(
                        python, "-X", "utf8", ENTRY_POINT,
                        "--request", requestFile.toAbsolutePath().toString(),
                        "--out", resultFile.toAbsolutePath().toString()));

                log.info("[MCE] Starting classification: input={}, hint={}, threshold={}",
                        request.getInputFile(), request.getHint(), request.getThreshold());

                ProcessBuilder pb = new ProcessBuilder(command)
                        .directory(scriptDir.toFile())
                        .redirectErrorStream(true);
                pb.environment().put("PYTHONIOENCODING", "utf-8");

                Process process = pb.start();
                String processOutput = readAll(process);

                if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    throw new ClassificationEngineException(
                            "Classification engine timed out after " + timeoutSeconds + "s");
                }

                int exit = process.exitValue();
                if (!Files.exists(resultFile)) {
                    throw new ClassificationEngineException(
                            "Engine produced no result (exit=" + exit + "): " + tail(processOutput));
                }

                ClassificationResult result =
                        objectMapper.readValue(resultFile.toFile(), ClassificationResult.class);
                if (!result.isOk() || exit != 0) {
                    throw new ClassificationEngineException(
                            "Engine run failed (exit=" + exit + "): "
                                    + (result.getError() != null ? result.getError() : tail(processOutput)));
                }

                log.info("[MCE] Classification done: engine={}, lines={}, needsReview={}",
                        result.getEngineVersion(), result.getTotalLines(), result.getNeedsReviewCount());
                return result;

            } catch (ClassificationEngineException e) {
                throw e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ClassificationEngineException("Classification interrupted", e);
            } catch (IOException e) {
                throw new ClassificationEngineException("Engine I/O failure: " + e.getMessage(), e);
            } finally {
                cleanup(workDir);
            }
        }
    }

    @Override
    public String healthProblem() {
        String dir = settings.get(ClassificationSettingsService.KEY_SCRIPT_DIR, "");
        if (dir.isEmpty()) {
            return "engine.script.dir is not configured (classification_settings)";
        }
        Path scriptDir = Path.of(dir);
        if (!Files.isDirectory(scriptDir)) {
            boolean looksLikeWindowsHostPath = dir.length() > 1 && dir.charAt(1) == ':';
            String hint = looksLikeWindowsHostPath
                    ? " (Windows host path is not visible inside Docker; set ENGINE_SCRIPT_DIR=/app/tools/classification-engine)"
                    : "";
            return "engine.script.dir does not exist: " + dir + hint;
        }
        for (String requiredFile : REQUIRED_ENGINE_FILES) {
            if (!Files.isRegularFile(scriptDir.resolve(requiredFile))) {
                return "Required classification engine file missing: " + scriptDir.resolve(requiredFile);
            }
        }
        String python = resolvePython(scriptDir);
        if (python.contains("/") || python.contains("\\")) {
            if (!Files.isRegularFile(Path.of(python))) {
                return "Python interpreter not found: " + python;
            }
        }
        String pythonProblem = pythonHealthProblem(python);
        if (pythonProblem != null) {
            return pythonProblem;
        }
        return null;
    }

    // ── internals ───────────────────────────────────────────────────────────

    private ObjectNode toEngineJson(ClassificationRequest request) {
        if (request.getInputFile() == null || request.getInputFile().isBlank()) {
            throw new ClassificationEngineException("inputFile is required");
        }
        ObjectNode node = objectMapper.createObjectNode();
        node.put("channel", request.getChannel().name());
        node.put("input_file", request.getInputFile());
        if (request.getReference() != null) node.put("reference", request.getReference());
        if (request.getThreshold() != null) node.put("threshold", request.getThreshold());
        if (request.getHint() != null) node.put("hint", request.getHint());
        if (request.getCodePrefix() != null) node.put("code_prefix", request.getCodePrefix());
        return node;
    }

    private Path requireScriptDir() {
        String dir = settings.get(ClassificationSettingsService.KEY_SCRIPT_DIR, "");
        if (dir.isEmpty()) {
            throw new ClassificationEngineException(
                    "engine.script.dir is not configured in classification_settings");
        }
        Path scriptDir = Path.of(dir);
        if (!Files.isRegularFile(scriptDir.resolve(ENTRY_POINT))) {
            throw new ClassificationEngineException(
                    ENTRY_POINT + " not found in engine.script.dir: " + dir);
        }
        return scriptDir;
    }

    /** Configured interpreter, else the script folder's venv, else python on PATH. */
    private String resolvePython(Path scriptDir) {
        String configured = settings.get(ClassificationSettingsService.KEY_PYTHON_PATH, "");
        if (!configured.isEmpty()) {
            return configured;
        }
        Path venvWin = scriptDir.resolve(".venv").resolve("Scripts").resolve("python.exe");
        if (Files.isRegularFile(venvWin)) {
            return venvWin.toAbsolutePath().toString();
        }
        Path venvNix = scriptDir.resolve(".venv").resolve("bin").resolve("python");
        if (Files.isRegularFile(venvNix)) {
            return venvNix.toAbsolutePath().toString();
        }
        return "python";
    }

    private String pythonHealthProblem(String python) {
        try {
            Process process = new ProcessBuilder(python, "--version")
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "Python interpreter health check timed out: " + python;
            }
            if (process.exitValue() != 0) {
                return "Python interpreter health check failed: " + python;
            }
            return null;
        } catch (IOException e) {
            return "Python interpreter could not be executed: " + python + " (" + e.getMessage() + ")";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Python interpreter health check interrupted: " + python;
        }
    }

    private String readAll(Process process) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
                if (sb.length() > 100_000) {
                    break; // cap log capture; the JSON result comes from the file
                }
            }
        }
        return sb.toString();
    }

    private String tail(String output) {
        if (output == null) return "";
        String trimmed = output.trim();
        return trimmed.length() <= 800 ? trimmed : "…" + trimmed.substring(trimmed.length() - 800);
    }

    private void cleanup(Path workDir) {
        if (workDir == null) return;
        try (var paths = Files.walk(workDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // temp dir; OS cleanup will get it eventually
                }
            });
        } catch (IOException e) {
            log.debug("[MCE] temp cleanup failed for {}: {}", workDir, e.getMessage());
        }
    }
}
