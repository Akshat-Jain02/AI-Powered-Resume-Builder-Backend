package com.latexcompiler.latexcompiler.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class LaTeXCompilerService {

    private static final String DEFAULT_PHOTO_NAME = "photo.png";

    private static final String DOCKER_IMAGE = "latex-compiler";
    private static final long TIMEOUT_SECONDS = 45;
    
    private static final String PDFLATEX = "pdflatex";
    private static final String XELATEX = "xelatex";
    private static final String LUALATEX = "lualatex";

    private static final String MAIN_TEX = "main.tex";
    private static final String MAIN_PDF = "main.pdf";
    private static final String DATA_DIR = "/data";

    private static final Pattern MAGIC_PATTERN = Pattern.compile("^\\s*%\\s*!TEX\\s+program\\s*=\\s*(\\w+)", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
    private static final Pattern BASE64_PREFIX_PATTERN = Pattern.compile("^data:[^,]*;base64,");
    // Matches \includegraphics with optional [...] args, capturing the {filename}
    private static final Pattern INCLUDEGRAPHICS_PATTERN = Pattern.compile(
            "\\\\includegraphics\\s*(?:\\[[^]]*\\])?\\s*\\{([^}]+)\\}");

    public static class CompilationResult {
        private byte[] pdfBytes;
        private String logs;
        private boolean success;

        public CompilationResult(byte[] pdfBytes, String logs, boolean success) {
            this.pdfBytes = pdfBytes;
            this.logs = logs;
            this.success = success;
        }

        public byte[] getPdfBytes() { return pdfBytes; }
        public String getLogs() { return logs; }
        public boolean isSuccess() { return success; }
    }

    @SuppressWarnings("java:S4036") // Absolute paths for docker/latex compilers vary by environment
    public CompilationResult compile(String latexCode, String photoBase64, Map<String, String> files) throws IOException, InterruptedException {
        String requestId = UUID.randomUUID().toString();
        Path baseDir = java.nio.file.Paths.get(System.getProperty("user.dir"), "latex-work");
        if (!Files.exists(baseDir)) {
            Files.createDirectories(baseDir);
        }
        Path tempDir = Files.createTempDirectory(baseDir, "latex-" + requestId);
        StringBuilder logsBuilder = new StringBuilder();
        
        try {
            writeProjectFiles(tempDir, latexCode, photoBase64, files);

            String initialCompiler = detectCompiler(latexCode);
            boolean dockerAvailable = isDockerImageAvailable();
            
            boolean success = executeCompilation(tempDir, initialCompiler, dockerAvailable, logsBuilder);

            File pdfFile = tempDir.resolve(MAIN_PDF).toFile();
            if (!success || !pdfFile.exists()) {
                return new CompilationResult(null, "PDF generation failed after multiple attempts.\nLogs:\n" + logsBuilder, false);
            }

            byte[] pdfBytes = FileUtils.readFileToByteArray(pdfFile);
            return new CompilationResult(pdfBytes, logsBuilder.toString(), true);

        } finally {
            FileUtils.deleteQuietly(tempDir.toFile());
        }
    }

    private void writeProjectFiles(Path tempDir, String latexCode, String photoBase64, Map<String, String> files) throws IOException {
        File texFile = tempDir.resolve(MAIN_TEX).toFile();
        
        // Fix common font-related macro compatibility issues
        String processedLatex = latexCode.replace("\\faMapMarkerAlt", "\\faMapMarker")
                             .replace("\\faPhoneAlt", "\\faPhone")
                             .replace("\\faLinkedinIn", "\\faLinkedin")
                             .replace("\\faExternalLinkAlt", "\\faExternalLink")
                             .replace("\\faEnvelopeOpenAlt", "\\faEnvelopeOpen");

        FileUtils.writeStringToFile(texFile, processedLatex, StandardCharsets.UTF_8);

        // Legacy single-photo support (form builder flow)
        // Write the photo with the filename referenced in the LaTeX code
        if (photoBase64 != null && !photoBase64.isBlank()) {
            String photoFileName = detectPhotoFileName(processedLatex);
            writeBase64File(tempDir, photoFileName, photoBase64);
            // Also write as fallback if a different name was used
            if (!DEFAULT_PHOTO_NAME.equals(photoFileName)) {
                writeBase64File(tempDir, DEFAULT_PHOTO_NAME, photoBase64);
            }
        }

        // Overleaf-style project files: write each file to the temp directory
        if (files != null && !files.isEmpty()) {
            for (Map.Entry<String, String> entry : files.entrySet()) {
                String fileName = sanitizeFileName(entry.getKey());
                if (fileName.isEmpty() || MAIN_TEX.equals(fileName)) continue; // skip main.tex overwrites
                writeBase64File(tempDir, fileName, entry.getValue());
            }
        }
    }

    private void writeBase64File(Path tempDir, String fileName, String content) throws IOException {
        if (content != null && !content.isBlank()) {
            try {
                if (content.startsWith("http")) {
                    // It's a URL (e.g. Cloudinary), download it
                    URL url = URI.create(content).toURL();
                    FileUtils.copyURLToFile(url, tempDir.resolve(fileName).toFile(), 10000, 10000);
                } else {
                    // It's Base64, decode it
                    String cleanB64 = BASE64_PREFIX_PATTERN.matcher(content).replaceAll("");
                    cleanB64 = cleanB64.replaceAll("\\s", "");
                    byte[] fileBytes = java.util.Base64.getDecoder().decode(cleanB64);
                    Files.write(tempDir.resolve(fileName), fileBytes);
                }
            } catch (Exception _) {
                // Log and continue, or handle as needed. We don't want to crash the whole compilation if one image fails.
                log.error("Failed to write file {}: connection or decoding error", fileName);
            }
        }
    }

    private boolean executeCompilation(Path tempDir, String compiler, boolean dockerAvailable, StringBuilder logsBuilder) throws IOException, InterruptedException {
        boolean triedPdflatex = PDFLATEX.equals(compiler);
        boolean triedXelatex = XELATEX.equals(compiler);
        boolean triedLualatex = LUALATEX.equals(compiler);

        String passLogs = runCompilationPass(compiler, tempDir, dockerAvailable);
        logsBuilder.append(passLogs);
        
        if (PDFLATEX.equals(compiler) && (passLogs.contains("font expansion") || passLogs.contains("scalable fonts") || passLogs.contains("Fatal error"))) {
            logsBuilder.append("\n--- pdflatex failed. Falling back to xelatex ---\n");
            compiler = XELATEX;
            passLogs = runCompilationPass(compiler, tempDir, dockerAvailable);
            logsBuilder.append(passLogs);
            triedXelatex = true;
        }
        
        compiler = tryFallbackCompilers(tempDir, compiler, dockerAvailable, logsBuilder, triedPdflatex, triedXelatex, triedLualatex);

        if (!tempDir.resolve(MAIN_PDF).toFile().exists()) {
            return false;
        }

        int passes = 1;
        while (shouldRerun(logsBuilder.toString()) && passes < 3) {
            logsBuilder.append("\n--- Running pass ").append(passes + 1).append(" ---\n");
            logsBuilder.append(runCompilationPass(compiler, tempDir, dockerAvailable));
            passes++;
        }
        return tempDir.resolve(MAIN_PDF).toFile().exists();
    }

    private String tryFallbackCompilers(Path tempDir, String compiler, boolean dockerAvailable, StringBuilder logsBuilder, 
                                      boolean triedPdflatex, boolean triedXelatex, boolean triedLualatex) throws IOException, InterruptedException {
        File pdfFileCheck = tempDir.resolve(MAIN_PDF).toFile();
        if (!pdfFileCheck.exists() && !triedPdflatex) {
            logsBuilder.append("\n--- Compilation failed. Falling back to pdflatex ---\n");
            compiler = PDFLATEX;
            logsBuilder.append(runCompilationPass(compiler, tempDir, dockerAvailable));
            pdfFileCheck = tempDir.resolve(MAIN_PDF).toFile();
        }
        
        if (!pdfFileCheck.exists() && !triedXelatex) {
            logsBuilder.append("\n--- Compilation failed. Trying xelatex fallback ---\n");
            compiler = XELATEX;
            logsBuilder.append(runCompilationPass(compiler, tempDir, dockerAvailable));
            pdfFileCheck = tempDir.resolve(MAIN_PDF).toFile();
        }
        
        if (!pdfFileCheck.exists() && !triedLualatex) {
            logsBuilder.append("\n--- Compilation failed. Trying lualatex (Ultimate Fallback) ---\n");
            compiler = LUALATEX;
            logsBuilder.append(runCompilationPass(compiler, tempDir, dockerAvailable));
        }
        return compiler;
    }

    String detectCompiler(String latexCode) {
        Matcher matcher = MAGIC_PATTERN.matcher(latexCode);
        if (matcher.find()) {
            String program = matcher.group(1).toLowerCase();
            if (PDFLATEX.equals(program) || XELATEX.equals(program) || LUALATEX.equals(program)) {
                return program;
            }
        }

        if (latexCode.contains("\\usepackage{fontspec}") || 
            latexCode.contains("\\setmainfont") ||
            latexCode.contains("\\usepackage{unicode-math}") ||
            latexCode.contains("\\usepackage{polyglossia}")) {
            return XELATEX;
        }

        if (latexCode.contains(LUALATEX)) {
            return LUALATEX;
        }
        
        if (latexCode.contains(XELATEX)) {
            return XELATEX;
        }

        return PDFLATEX;
    }

    /**
     * Sanitize a filename to prevent path-traversal attacks.
     * Strips directory separators and ensures the file stays in the temp directory.
     */
    String sanitizeFileName(String name) {
        if (name == null) return "";
        // Remove any path separators and parent-directory references
        String sanitized = name.replace("\\", "/");
        // Take only the filename portion (after the last /)
        int lastSlash = sanitized.lastIndexOf('/');
        if (lastSlash >= 0) {
            sanitized = sanitized.substring(lastSlash + 1);
        }
        // Remove leading dots to prevent hidden files / directory traversal
        sanitized = sanitized.replaceAll("^\\.+", "");
        return sanitized.trim();
    }

    /**
     * Scans the LaTeX code for the first \includegraphics reference that looks
     * like a user-uploaded image file (png, jpg, jpeg, etc.) and returns that
     * filename. Falls back to "photo.png" if nothing is found.
     */
    String detectPhotoFileName(String latexCode) {
        Matcher m = INCLUDEGRAPHICS_PATTERN.matcher(latexCode);
        while (m.find()) {
            String filename = m.group(1).trim();
            // Skip common non-photo filenames like logos or icons
            String lower = filename.toLowerCase();
            if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                    || lower.endsWith(".gif") || lower.endsWith(".pdf") || lower.endsWith(".eps")) {
                return filename;
            }
        }
        return DEFAULT_PHOTO_NAME;
    }

    boolean shouldRerun(String logs) {
        return logs.contains("Rerun to get cross-references right") ||
               logs.contains("Rerun to get outlines right") ||
               logs.contains("Rerun LaTeX") ||
               logs.contains("Warning: Label(s) may have changed") ||
               logs.contains("Warning: Citation") ||
               logs.contains("There were undefined references");
    }

    @SuppressWarnings("java:S4036")
    String runCompilationPass(String compiler, Path tempDir, boolean dockerAvailable) throws IOException, InterruptedException {
        if (!PDFLATEX.equals(compiler) && !XELATEX.equals(compiler) && !LUALATEX.equals(compiler)) {
            throw new IllegalArgumentException("Invalid compiler: " + compiler);
        }

        ProcessBuilder pb;
        if (dockerAvailable) {
            pb = new ProcessBuilder(
                    "docker", "run", "--rm",
                    "-v", tempDir.toAbsolutePath().toString().replace("\\", "/") + ":" + DATA_DIR,
                    DOCKER_IMAGE,
                    compiler, "-interaction=nonstopmode", "-shell-escape", "-output-directory=" + DATA_DIR, DATA_DIR + "/" + MAIN_TEX
            );
        } else {
            pb = new ProcessBuilder(
                    compiler, "-interaction=nonstopmode", "-shell-escape",
                    MAIN_TEX
            );
            pb.directory(tempDir.toFile());
        }

        pb.redirectErrorStream(true);
        Process process = pb.start();
        try {
            String passLogs = IOUtils.toString(process.getInputStream(), StandardCharsets.UTF_8);
            
            boolean completed = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return "Compilation timed out (" + compiler + ").\nLogs:\n" + passLogs;
            }
            return passLogs;
        } finally {
            process.destroy();
        }
    }

    @SuppressWarnings("java:S4036")
    boolean isDockerImageAvailable() {
        Process process = null;
        try {
            process = new ProcessBuilder("docker", "images", "-q", DOCKER_IMAGE).start();
            String output = IOUtils.toString(process.getInputStream(), StandardCharsets.UTF_8);
            process.waitFor(5, TimeUnit.SECONDS);
            return !output.trim().isEmpty();
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception _) {
            return false;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }
}
