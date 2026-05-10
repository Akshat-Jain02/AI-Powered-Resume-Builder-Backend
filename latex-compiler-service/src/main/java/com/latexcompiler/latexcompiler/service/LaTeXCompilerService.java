package com.latexcompiler.latexcompiler.service;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LaTeXCompilerService {

    private static final String DOCKER_IMAGE = "latex-compiler";
    private static final long TIMEOUT_SECONDS = 45;
    
    private static final String PDFLATEX = "pdflatex";
    private static final String XELATEX = "xelatex";
    private static final String LUALATEX = "lualatex";

    private static final String MAIN_TEX = "main.tex";
    private static final String MAIN_PDF = "main.pdf";
    private static final String DATA_DIR = "/data";

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
    public CompilationResult compile(String latexCode, String photoBase64) throws IOException, InterruptedException {
        String requestId = UUID.randomUUID().toString();
        Path baseDir = java.nio.file.Paths.get(System.getProperty("user.dir"), "latex-work");
        if (!Files.exists(baseDir)) {
            Files.createDirectories(baseDir);
        }
        Path tempDir = Files.createTempDirectory(baseDir, "latex-" + requestId);
        File texFile = tempDir.resolve(MAIN_TEX).toFile();
        
        StringBuilder logsBuilder = new StringBuilder();
        try {
            // Fix common font-related macro compatibility issues
            latexCode = latexCode.replace("\\faMapMarkerAlt", "\\faMapMarker")
                                 .replace("\\faPhoneAlt", "\\faPhone")
                                 .replace("\\faLinkedinIn", "\\faLinkedin")
                                 .replace("\\faExternalLinkAlt", "\\faExternalLink")
                                 .replace("\\faEnvelopeOpenAlt", "\\faEnvelopeOpen");

            FileUtils.writeStringToFile(texFile, latexCode, StandardCharsets.UTF_8);

            if (photoBase64 != null && !photoBase64.isBlank()) {
                byte[] imgBytes = java.util.Base64.getDecoder().decode(
                        photoBase64.replaceAll("^data:image/[^;]+;base64,", ""));
                Files.write(tempDir.resolve("photo.png"), imgBytes);
            }

            String compiler = detectCompiler(latexCode);
            boolean dockerAvailable = isDockerImageAvailable();
            
            String passLogs = runCompilationPass(compiler, tempDir, dockerAvailable);
            logsBuilder.append(passLogs);
            
            if (compiler.equals(PDFLATEX) && (passLogs.contains("font expansion") || passLogs.contains("scalable fonts") || passLogs.contains("Fatal error"))) {
                logsBuilder.append("\n--- pdflatex failed. Falling back to xelatex ---\n");
                compiler = XELATEX;
                passLogs = runCompilationPass(compiler, tempDir, dockerAvailable);
                logsBuilder.append(passLogs);
            }
            
            File pdfFileCheck = tempDir.resolve(MAIN_PDF).toFile();
            if (!pdfFileCheck.exists() && !compiler.equals(LUALATEX)) {
                logsBuilder.append("\n--- Compilation failed. Trying lualatex (Ultimate Fallback) ---\n");
                compiler = LUALATEX;
                passLogs = runCompilationPass(compiler, tempDir, dockerAvailable);
                logsBuilder.append(passLogs);
            }

            int passes = 1;
            while (shouldRerun(logsBuilder.toString()) && passes < 3) {
                logsBuilder.append("\n--- Running pass ").append(passes + 1).append(" ---\n");
                logsBuilder.append(runCompilationPass(compiler, tempDir, dockerAvailable));
                passes++;
            }

            File pdfFile = tempDir.resolve(MAIN_PDF).toFile();
            if (!pdfFile.exists()) {
                return new CompilationResult(null, "PDF generation failed after multiple attempts.\nLogs:\n" + logsBuilder, false);
            }

            byte[] pdfBytes = FileUtils.readFileToByteArray(pdfFile);
            return new CompilationResult(pdfBytes, logsBuilder.toString(), true);

        } finally {
            FileUtils.deleteQuietly(tempDir.toFile());
        }
    }

    String detectCompiler(String latexCode) {
        Pattern magicPattern = Pattern.compile("^\\s*%\\s*!TEX\\s+program\\s*=\\s*(\\w+)", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
        Matcher matcher = magicPattern.matcher(latexCode);
        if (matcher.find()) {
            String program = matcher.group(1).toLowerCase();
            if (program.matches("pdflatex|xelatex|lualatex")) {
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
        
        if (latexCode.contains("\\usepackage{fontawesome5}") || 
            latexCode.contains(XELATEX)) {
            return XELATEX;
        }

        return PDFLATEX;
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
        if (!compiler.matches("pdflatex|xelatex|lualatex")) {
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
        String passLogs = IOUtils.toString(process.getInputStream(), StandardCharsets.UTF_8);
        
        boolean completed = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            return "Compilation timed out (" + compiler + ").\nLogs:\n" + passLogs;
        }
        return passLogs;
    }

    @SuppressWarnings("java:S4036")
    boolean isDockerImageAvailable() {
        try {
            Process process = new ProcessBuilder("docker", "images", "-q", DOCKER_IMAGE).start();
            String output = IOUtils.toString(process.getInputStream(), StandardCharsets.UTF_8);
            return !output.trim().isEmpty();
        } catch (Exception _) {
            return false;
        }
    }
}
