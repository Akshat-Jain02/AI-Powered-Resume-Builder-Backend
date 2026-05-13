package com.latexcompiler.latexcompiler.service;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LaTeXCompilerServiceTest {

    private LaTeXCompilerService service;
    private LaTeXCompilerService spyService;
    private MockedStatic<Files> mockedFiles;
    private MockedStatic<FileUtils> mockedFileUtils;

    @BeforeEach
    void setUp() {
        service = new LaTeXCompilerService();
        spyService = spy(service);
        doReturn(false).when(spyService).isDockerImageAvailable();
        mockedFiles = mockStatic(Files.class);
        mockedFileUtils = mockStatic(FileUtils.class);
    }

    @AfterEach
    void tearDown() {
        mockedFiles.close();
        mockedFileUtils.close();
    }

    @Test
    void testDetectCompiler_MagicComment() {
        assertEquals("lualatex", service.detectCompiler("% !TEX program = lualatex\n\\documentclass{article}"));
        assertEquals("xelatex", service.detectCompiler("% !TEX program = xelatex\n\\documentclass{article}"));
        assertEquals("pdflatex", service.detectCompiler("% !TEX program = pdflatex\n\\documentclass{article}"));
    }

    @Test
    void testDetectCompiler_Features() {
        assertEquals("xelatex", service.detectCompiler("\\usepackage{fontspec}"));
        assertEquals("xelatex", service.detectCompiler("\\setmainfont{Arial}"));
        assertEquals("pdflatex", service.detectCompiler("\\usepackage{fontawesome5}"));
        assertEquals("lualatex", service.detectCompiler("Some text with lualatex keyword"));
        assertEquals("pdflatex", service.detectCompiler("\\documentclass{article}"));
    }

    @Test
    void testCompile_Success() throws IOException, InterruptedException {
        String latexCode = "\\documentclass{article}\\begin{document}Hello\\end{document}";
        byte[] expectedPdf = "fake-pdf-content".getBytes();
        
        Path mockPath = mock(Path.class);
        File mockPdfFile = mock(File.class);
        
        mockedFiles.when(() -> Files.exists(any(Path.class))).thenReturn(true);
        mockedFiles.when(() -> Files.createTempDirectory(any(Path.class), anyString())).thenReturn(mockPath);
        when(mockPath.resolve(anyString())).thenReturn(mockPath);
        when(mockPath.toFile()).thenReturn(mock(File.class));
        
        // Mock PDF file exists and has content
        when(mockPdfFile.exists()).thenReturn(true);
        when(mockPath.toFile()).thenReturn(mockPdfFile); // for the pdf file check
        
        // Refine path resolution mocking
        Path pdfPath = mock(Path.class);
        when(mockPath.resolve("main.pdf")).thenReturn(pdfPath);
        when(pdfPath.toFile()).thenReturn(mockPdfFile);
        
        doReturn("Output Log").when(spyService).runCompilationPass(anyString(), any(), anyBoolean());
        doReturn(false).when(spyService).isDockerImageAvailable();
        mockedFileUtils.when(() -> FileUtils.readFileToByteArray(mockPdfFile)).thenReturn(expectedPdf);

        LaTeXCompilerService.CompilationResult result = spyService.compile(latexCode, null, null);

        assertTrue(result.isSuccess());
        assertArrayEquals(expectedPdf, result.getPdfBytes());
        verify(spyService, atLeastOnce()).runCompilationPass(eq("pdflatex"), any(), anyBoolean());
    }

    @Test
    void testCompile_WithPhoto() throws IOException, InterruptedException {
        String latexCode = "\\documentclass{article}";
        String photoBase64 = Base64.getEncoder().encodeToString("fake-image".getBytes());
        
        Path mockPath = mock(Path.class);
        when(mockPath.resolve(anyString())).thenReturn(mockPath);
        mockedFiles.when(() -> Files.exists(any(Path.class))).thenReturn(true);
        mockedFiles.when(() -> Files.createTempDirectory(any(Path.class), anyString())).thenReturn(mockPath);
        
        doReturn("Logs").when(spyService).runCompilationPass(anyString(), any(), anyBoolean());
        
        // Just verify it doesn't crash and tries to write the photo
        try {
            spyService.compile(latexCode, photoBase64, null);
        } catch (Exception _) {
            // Expected to fail later in the method because we didn't mock everything, 
            // but we want to check if Files.write was called
        }
        
        mockedFiles.verify(() -> Files.write(eq(mockPath), any(byte[].class)));
    }

    @Test
    void testCompile_FallbackToXeLaTeX() throws IOException, InterruptedException {
        String latexCode = "\\documentclass{article}";
        
        Path mockPath = mock(Path.class);
        File mockPdfFile = mock(File.class);
        Path pdfPath = mock(Path.class);
        
        mockedFiles.when(() -> Files.exists(any(Path.class))).thenReturn(true);
        mockedFiles.when(() -> Files.createTempDirectory(any(Path.class), anyString())).thenReturn(mockPath);
        when(mockPath.resolve(anyString())).thenReturn(mockPath);
        when(mockPath.resolve("main.pdf")).thenReturn(pdfPath);
        when(pdfPath.toFile()).thenReturn(mockPdfFile);
        when(mockPdfFile.exists()).thenReturn(true);
        
        // Mock pdflatex failure with font error
        doReturn("Fatal error: font expansion failed").when(spyService).runCompilationPass(eq("pdflatex"), any(), anyBoolean());
        // Mock xelatex success
        doReturn("XeLaTeX Success").when(spyService).runCompilationPass(eq("xelatex"), any(), anyBoolean());
        
        mockedFileUtils.when(() -> FileUtils.readFileToByteArray(any())).thenReturn("pdf".getBytes());

        spyService.compile(latexCode, null, null);

        verify(spyService).runCompilationPass(eq("pdflatex"), any(), anyBoolean());
        verify(spyService).runCompilationPass(eq("xelatex"), any(), anyBoolean());
    }

    @Test
    void testDetectCompiler_MoreFeatures() {
        assertEquals("xelatex", service.detectCompiler("\\usepackage{unicode-math}"));
        assertEquals("xelatex", service.detectCompiler("\\usepackage{polyglossia}"));
        assertEquals("xelatex", service.detectCompiler("xelatex explicitly mentioned"));
        assertEquals("pdflatex", service.detectCompiler("% !TEX program = unknown\n\\documentclass{article}"));
    }

    @Test
    void testCompile_MultiPass() throws IOException, InterruptedException {
        String latexCode = "\\documentclass{article}";
        
        Path mockPath = mock(Path.class);
        File mockPdfFile = mock(File.class);
        Path pdfPath = mock(Path.class);
        
        mockedFiles.when(() -> Files.exists(any(Path.class))).thenReturn(true);
        mockedFiles.when(() -> Files.createTempDirectory(any(Path.class), anyString())).thenReturn(mockPath);
        when(mockPath.resolve(anyString())).thenReturn(mockPath);
        when(mockPath.resolve("main.pdf")).thenReturn(pdfPath);
        when(pdfPath.toFile()).thenReturn(mockPdfFile);
        when(mockPdfFile.exists()).thenReturn(true);
        
        // Mock rerun trigger
        doReturn("Rerun to get cross-references right").when(spyService).runCompilationPass(anyString(), any(), anyBoolean());
        
        mockedFileUtils.when(() -> FileUtils.readFileToByteArray(any())).thenReturn("pdf".getBytes());

        spyService.compile(latexCode, null, null);

        // Should run at least 2 passes
        verify(spyService, atLeast(2)).runCompilationPass(anyString(), any(), anyBoolean());
    }

    @Test
    void testCompile_WithDocker() throws IOException, InterruptedException {
        String latexCode = "\\documentclass{article}";
        
        Path mockPath = mock(Path.class);
        File mockPdfFile = mock(File.class);
        Path pdfPath = mock(Path.class);
        
        mockedFiles.when(() -> Files.exists(any(Path.class))).thenReturn(true);
        mockedFiles.when(() -> Files.createTempDirectory(any(Path.class), anyString())).thenReturn(mockPath);
        when(mockPath.resolve(anyString())).thenReturn(mockPath);
        when(mockPath.resolve("main.pdf")).thenReturn(pdfPath);
        when(pdfPath.toFile()).thenReturn(mockPdfFile);
        when(mockPdfFile.exists()).thenReturn(true);
        
        doReturn(true).when(spyService).isDockerImageAvailable();
        doReturn("Docker Logs").when(spyService).runCompilationPass(anyString(), any(), eq(true));
        
        mockedFileUtils.when(() -> FileUtils.readFileToByteArray(any())).thenReturn("pdf".getBytes());

        spyService.compile(latexCode, null, null);

        verify(spyService).runCompilationPass(anyString(), any(), eq(true));
    }

    @Test
    void testCompile_Timeout() throws IOException, InterruptedException {
        Path mockPath = mock(Path.class);
        File mockFile = mock(File.class);
        mockedFiles.when(() -> Files.exists(any(Path.class))).thenReturn(true);
        mockedFiles.when(() -> Files.createTempDirectory(any(Path.class), anyString())).thenReturn(mockPath);
        when(mockPath.resolve(anyString())).thenReturn(mockPath);
        when(mockPath.toFile()).thenReturn(mockFile);
        when(mockFile.exists()).thenReturn(false);
        
        // Simulating a timeout by returning a timeout message from runCompilationPass
        // Since runCompilationPass is where the timeout is handled in the real code, 
        // we can just return the message it would return.
        doReturn("Compilation timed out (pdflatex).").when(spyService).runCompilationPass(anyString(), any(), anyBoolean());

        LaTeXCompilerService.CompilationResult result = spyService.compile("code", null, null);

        assertFalse(result.isSuccess());
        assertTrue(result.getLogs().contains("timed out"));
    }

    @Test
    void testShouldRerunTriggers() {
        assertTrue(service.shouldRerun("Rerun to get cross-references right"));
        assertTrue(service.shouldRerun("Rerun to get outlines right"));
        assertTrue(service.shouldRerun("Rerun LaTeX"));
        assertTrue(service.shouldRerun("Warning: Label(s) may have changed"));
        assertTrue(service.shouldRerun("Warning: Citation"));
        assertTrue(service.shouldRerun("There were undefined references"));
        assertFalse(service.shouldRerun("All good"));
    }

    @Test
    void testSanitizeFileName() {
        assertEquals("", service.sanitizeFileName(null));
        assertEquals("file.txt", service.sanitizeFileName("file.txt"));
        assertEquals("file.txt", service.sanitizeFileName("/etc/passwd/file.txt"));
        assertEquals("file.txt", service.sanitizeFileName("..\\..\\windows\\file.txt"));
        assertEquals("file.txt", service.sanitizeFileName("...file.txt"));
    }

    @Test
    void testDetectCompiler_EdgeCases() {
        assertEquals("pdflatex", service.detectCompiler("% !TEX program = invalid\n\\documentclass{article}"));
        assertEquals("pdflatex", service.detectCompiler(""));
        assertEquals("lualatex", service.detectCompiler("lualatex"));
        assertEquals("xelatex", service.detectCompiler("xelatex"));
    }

    @Test
    void testCompile_WithFilesMap() throws IOException, InterruptedException {
        String latexCode = "\\documentclass{article}";
        java.util.Map<String, String> files = new java.util.HashMap<>();
        files.put("photo.png", Base64.getEncoder().encodeToString("fake-image".getBytes()));
        files.put("main.tex", "should be ignored");
        files.put("", "should be ignored");
        
        Path mockPath = mock(Path.class);
        File mockPdfFile = mock(File.class);
        Path pdfPath = mock(Path.class);
        
        mockedFiles.when(() -> Files.exists(any(Path.class))).thenReturn(true);
        mockedFiles.when(() -> Files.createTempDirectory(any(Path.class), anyString())).thenReturn(mockPath);
        when(mockPath.resolve(anyString())).thenReturn(mockPath);
        when(mockPath.resolve("main.pdf")).thenReturn(pdfPath);
        when(pdfPath.toFile()).thenReturn(mockPdfFile);
        when(mockPdfFile.exists()).thenReturn(true);
        
        doReturn("Logs").when(spyService).runCompilationPass(anyString(), any(), anyBoolean());
        mockedFileUtils.when(() -> FileUtils.readFileToByteArray(any())).thenReturn("pdf".getBytes());
        
        LaTeXCompilerService.CompilationResult result = spyService.compile(latexCode, null, files);
        
        assertTrue(result.isSuccess());
        // Verify files were written
        mockedFiles.verify(() -> Files.write(eq(mockPath), any(byte[].class)), atLeastOnce());
    }

    @Test
    void testIsDockerImageAvailable_Exception() throws IOException {
        LaTeXCompilerService exceptionService = new LaTeXCompilerService() {
            @Override
            boolean isDockerImageAvailable() {
                return super.isDockerImageAvailable(); // Call real method for exception testing
            }
        };
        assertFalse(exceptionService.isDockerImageAvailable());
    }

    @Test
    void testRunCompilationPass_InvalidCompiler() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            service.runCompilationPass("invalid", mock(Path.class), false);
        });
        assertTrue(exception.getMessage().contains("Invalid compiler"));
    }

    @Test
    void testCompile_UltimateFallbackToLuaLaTeX() throws IOException, InterruptedException {
        String latexCode = "\\documentclass{article}";
        
        Path mockPath = mock(Path.class);
        File mockPdfFile = mock(File.class);
        Path pdfPath = mock(Path.class);
        
        mockedFiles.when(() -> Files.exists(any(Path.class))).thenReturn(true);
        mockedFiles.when(() -> Files.createTempDirectory(any(Path.class), anyString())).thenReturn(mockPath);
        when(mockPath.resolve(anyString())).thenReturn(mockPath);
        when(mockPath.resolve("main.pdf")).thenReturn(pdfPath);
        when(pdfPath.toFile()).thenReturn(mockPdfFile);
        
        // Mock pdf file exists only on the 3rd fallback (lualatex)
        when(mockPdfFile.exists()).thenReturn(false, false, false, true, true);
        
        // Mock compilation logs
        doReturn("Fatal error").when(spyService).runCompilationPass(eq("pdflatex"), any(), anyBoolean());
        doReturn("XeLaTeX Failed").when(spyService).runCompilationPass(eq("xelatex"), any(), anyBoolean());
        doReturn("LuaLaTeX Success").when(spyService).runCompilationPass(eq("lualatex"), any(), anyBoolean());
        
        mockedFileUtils.when(() -> FileUtils.readFileToByteArray(any())).thenReturn("pdf".getBytes());

        LaTeXCompilerService.CompilationResult result = spyService.compile(latexCode, null, null);

        assertTrue(result.isSuccess());
        verify(spyService).runCompilationPass(eq("pdflatex"), any(), anyBoolean());
        verify(spyService).runCompilationPass(eq("xelatex"), any(), anyBoolean());
        verify(spyService).runCompilationPass(eq("lualatex"), any(), anyBoolean());
    }
}
