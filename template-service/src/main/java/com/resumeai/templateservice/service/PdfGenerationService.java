package com.resumeai.templateservice.service;

import com.resumeai.templateservice.dto.ResumeDataDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Responsible for converting resume data into a PDF by:
 *  1. Building a LaTeX document string for the requested template.
 *  2. Writing any embedded assets (e.g. profile photo) to a temp directory.
 *  3. Compiling the LaTeX with {@code pdflatex} (run twice for stable layout).
 *  4. Returning the raw PDF bytes.
 *
 * Templates 1 (Executive Classic), 3 (Creative Crimson) and 4 (Minimalist Ivory)
 * support an optional circular profile photo.  The photo is passed as a Base64
 * string in {@link ResumeDataDto#getPhotoBase64()} and is written to disk as a
 * PNG before compilation so that the LaTeX {@code \includegraphics} command can
 * find it.
 */
@Service
@Slf4j
public class PdfGenerationService {

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Generate a PDF for the given template index (1-based) and resume data.
     *
     * @param templateId 1–6 (matches seeded template rows)
     * @param data       resume data populated by the user
     * @return raw PDF bytes ready to stream to the client
     */
    public byte[] generatePdf(int templateId, ResumeDataDto data) throws Exception {
        String latex = buildLatex(templateId, data);
        return compilePdf(latex, data.getPhotoBase64());
    }

    // ── Template dispatch ──────────────────────────────────────────────────────

    private String buildLatex(int id, ResumeDataDto d) {
        log.info("Building LaTeX for template mapping id: {}", id);
        return switch (id) {
            case 1  -> template1ClassicBlue(d);
            case 2  -> template2Editorial(d);
            case 3  -> template3DarkSidebar(d);
            case 4  -> template4PurpleExecutive(d);
            case 5  -> template5BoldRed(d);
            case 6  -> template6ATSBlue(d);
            default -> {
                log.error("Unsupported template mapping ID: {}. Falling back to Template 1.", id);
                yield template1ClassicBlue(d);
            }
        };
    }

    // ── Template 1: Executive Classic (Blue) — supports photo ─────────────────

    private String template1ClassicBlue(ResumeDataDto d) {
        StringBuilder sb = new StringBuilder();
        sb.append(latexPreamble("1a3a5c", "helvet", hasPhoto(d)));
        sb.append("""
                \\definecolor{accentblue}{HTML}{1a3a5c}
                \\definecolor{lightbg}{HTML}{f0f4f9}
                \\definecolor{accenttext}{HTML}{7fb3d3}
                \\definecolor{headerfg}{HTML}{b8d4ea}
                """);
        sb.append(latexDocStart());

        // ── Header ────────────────────────────────────────────────────────────
        sb.append("\\colorbox{accentblue}{%\n");
        sb.append("\\begin{minipage}{\\textwidth}\\vspace{10pt}\\hspace{14pt}\n");

        if (hasPhoto(d)) {
            // Header split: photo left, text right
            sb.append("\\begin{minipage}[c]{0.15\\textwidth}\n");
            sb.append("  \\circphoto{2.2cm}{white!40}\n");
            sb.append("\\end{minipage}\\hspace{6pt}\n");
            sb.append("\\begin{minipage}[c]{0.78\\textwidth}\n");
        } else {
            sb.append("\\begin{minipage}{0.9\\textwidth}\n");
        }

        sb.append("  {\\color{white}\\fontsize{22}{26}\\bfseries\\selectfont ").append(esc(d.getFullName())).append("}\\par\\vspace{3pt}\n");
        sb.append("  {\\color{accenttext}\\fontsize{10}{13}\\selectfont ").append(esc(d.getTargetJobTitle())).append("}\\par\\vspace{6pt}\n");
        sb.append("  {\\color{headerfg}\\fontsize{8.5}{11}\\selectfont ").append(contactLine(d, " \\textbullet\\ ")).append("}\\vspace{10pt}\n");
        sb.append("\\end{minipage}\\end{minipage}}\\par\\vspace{0pt}\n\n");

        // ── Body: left main, right sidebar ────────────────────────────────────
        sb.append("\\begin{minipage}[t]{0.60\\textwidth}\\vspace{10pt}\\hspace{12pt}\n");
        sb.append("\\begin{minipage}[t]{0.9\\textwidth}\n");

        if (notBlank(d.getSummary())) {
            sb.append(sectionHeader("Professional Summary", "accentblue"));
            sb.append("{\\fontsize{9}{13}\\selectfont\\color{textgray} ").append(esc(d.getSummary())).append("}\\par\\vspace{8pt}\n");
        }

        if (hasItems(d.getExperience())) {
            sb.append(sectionHeader("Work Experience", "accentblue"));
            for (ResumeDataDto.ExperienceDto e : d.getExperience()) {
                if (blank(e.getPosition()) && blank(e.getCompany())) continue;
                sb.append("\\textbf{\\fontsize{10}{13}\\selectfont ").append(esc(e.getPosition())).append("}\\hfill{\\small\\color{lighttext}\\textit{").append(dateLine(e)).append("}}\\par\n");
                sb.append("{\\color{accentblue}\\fontsize{9}{12}\\selectfont\\bfseries ").append(esc(e.getCompany())).append("}");
                if (notBlank(e.getLocation())) sb.append(" \\hfill {\\footnotesize\\color{lighttext}\\itshape ").append(esc(e.getLocation())).append("}");
                sb.append("\\par\n");
                appendBullets(sb, e);
                sb.append("\\vspace{6pt}\n");
            }
        }

        if (hasItems(d.getProjects())) {
            sb.append(sectionHeader("Projects", "accentblue"));
            for (ResumeDataDto.ProjectDto p : d.getProjects()) {
                if (blank(p.getName())) continue;
                sb.append("\\textbf{\\fontsize{9.5}{12}\\selectfont ").append(esc(p.getName())).append("}");
                String tech = notBlank(p.getTechStack()) ? p.getTechStack() : p.getTechnologies();
                if (notBlank(tech)) sb.append(" --- {\\small\\color{accentblue} ").append(esc(tech)).append("}");
                sb.append("\\par\n");
                if (notBlank(p.getDescription())) sb.append("{\\footnotesize\\color{textgray} ").append(esc(p.getDescription())).append("}\\par\n");
                sb.append("\\vspace{5pt}\n");
            }
        }

        sb.append("\\end{minipage}\\end{minipage}%\n\\hfill\n");

        // ── Right sidebar ──────────────────────────────────────────────────────
        sb.append("\\begin{minipage}[t]{0.38\\textwidth}\n");
        sb.append("\\colorbox{lightbg}{\\begin{minipage}[t][260mm][t]{\\textwidth}\\vspace{10pt}\\hspace{6pt}\n");
        sb.append("\\begin{minipage}[t]{0.88\\textwidth}\n");

        if (hasItems(d.getSkills())) {
            sb.append(sectionHeader("Skills", "accentblue"));
            for (String s : d.getSkills()) {
                if (blank(s)) continue;
                sb.append("\\colorbox{accentblue}{\\color{white}\\fontsize{8}{10}\\selectfont\\strut ").append(esc(s)).append("} ");
            }
            sb.append("\\par\\vspace{8pt}\n");
        }

        if (hasItems(d.getEducation())) {
            sb.append(sectionHeader("Education", "accentblue"));
            for (ResumeDataDto.EducationDto e : d.getEducation()) {
                if (blank(e.getDegree()) && blank(e.getInstitution())) continue;
                sb.append("\\textbf{\\fontsize{9}{12}\\selectfont ").append(esc(e.getDegree())).append("}\\par\n");
                if (notBlank(e.getField())) sb.append("{\\fontsize{8}{11}\\selectfont\\color{textgray} ").append(esc(e.getField())).append("}\\par\n");
                sb.append("{\\fontsize{8.5}{11}\\color{accentblue}\\bfseries\\selectfont ").append(esc(e.getInstitution())).append("}\\par\n");
                sb.append("{\\footnotesize\\color{lighttext} ").append(esc(e.getStartDate())).append("--").append(esc(e.getEndDate())).append("}\\par\n");
                if (notBlank(e.getGrade())) sb.append("{\\footnotesize\\color{lighttext} GPA/Grade: ").append(esc(e.getGrade())).append("}\\par\n");
                sb.append("\\vspace{6pt}\n");
            }
        }

        if (hasItems(d.getCertifications())) {
            sb.append(sectionHeader("Certifications", "accentblue"));
            for (ResumeDataDto.CertificationDto c : d.getCertifications()) {
                if (blank(c.getName())) continue;
                sb.append("\\textbf{\\fontsize{9}{12}\\selectfont ").append(esc(c.getName())).append("}\\par\n");
                String yr = notBlank(c.getDate()) ? c.getDate() : (notBlank(c.getYear()) ? c.getYear() : "");
                sb.append("{\\footnotesize\\color{textgray} ").append(esc(c.getIssuer())).append(notBlank(yr) ? " $\\cdot$ " + esc(yr) : "").append("}\\par\n");
                sb.append("\\vspace{5pt}\n");
            }
        }

        sb.append("\\end{minipage}\\end{minipage}}\\end{minipage}\n");
        sb.append("\\end{document}\n");
        return sb.toString();
    }

    // ── Template 2: Modern Slate (Editorial Serif) — no photo ─────────────────

    private String template2Editorial(ResumeDataDto d) {
        StringBuilder sb = new StringBuilder();
        sb.append(latexPreamble("1a1a1a", "palatino", false));
        sb.append("\\definecolor{accentcolor}{HTML}{1a1a1a}\n");
        sb.append(latexDocStart());

        sb.append("{\\color{accentcolor}\\rule{\\textwidth}{1.5pt}}\\par\\vspace{6pt}\n");
        sb.append("{\\fontsize{24}{28}\\selectfont\\textls[150]{").append(esc(d.getFullName()).toUpperCase()).append("}}\\par\\vspace{3pt}\n");
        sb.append("{\\fontsize{11}{14}\\itshape\\color{textgray} ").append(esc(d.getTargetJobTitle())).append("}\\par\\vspace{6pt}\n");
        sb.append("{\\footnotesize\\color{lighttext} ").append(contactLine(d, " \\quad ")).append("}\\par\\vspace{4pt}\n");
        sb.append("{\\color{accentcolor}\\rule{\\textwidth}{1.5pt}}\\par\\vspace{10pt}\n");

        if (notBlank(d.getSummary())) {
            sb.append(sectionHeader("Summary", "accentcolor"));
            sb.append("{\\fontsize{9.5}{14}\\selectfont\\color{textgray} ").append(esc(d.getSummary())).append("}\\par\\vspace{8pt}\n");
        }

        if (hasItems(d.getExperience())) {
            sb.append(sectionHeader("Work Experience", "accentcolor"));
            for (ResumeDataDto.ExperienceDto e : d.getExperience()) {
                if (blank(e.getPosition()) && blank(e.getCompany())) continue;
                sb.append("\\textbf{").append(esc(e.getPosition())).append("}\\hfill{\\small\\itshape\\color{lighttext} ").append(dateLine(e)).append("}\\par\n");
                sb.append("{\\color{accentcolor}\\bfseries\\small ").append(esc(e.getCompany())).append("}\\par\n");
                appendBullets(sb, e);
                sb.append("\\vspace{6pt}\n");
            }
        }

        // Two-column lower section
        sb.append("\\begin{minipage}[t]{0.48\\textwidth}\n");
        if (hasItems(d.getEducation())) {
            sb.append(sectionHeader("Education", "accentcolor"));
            for (ResumeDataDto.EducationDto e : d.getEducation()) {
                if (blank(e.getDegree()) && blank(e.getInstitution())) continue;
                sb.append("\\textbf{\\small ").append(esc(e.getDegree())).append("}\\par\n");
                if (notBlank(e.getField())) sb.append("{\\footnotesize\\itshape\\color{textgray} ").append(esc(e.getField())).append("}\\par\n");
                sb.append("{\\small\\bfseries\\color{accentcolor} ").append(esc(e.getInstitution())).append("}\\par\n");
                sb.append("{\\footnotesize\\color{lighttext} ").append(esc(e.getStartDate())).append("--").append(esc(e.getEndDate())).append("}\\par\n");
                if (notBlank(e.getGrade())) sb.append("{\\footnotesize\\color{lighttext} GPA/Grade: ").append(esc(e.getGrade())).append("}\\par\\vspace{5pt}\n");
                else sb.append("\\vspace{5pt}\n");
            }
        }
        if (hasItems(d.getCertifications())) {
            sb.append(sectionHeader("Certifications", "accentcolor"));
            for (ResumeDataDto.CertificationDto c : d.getCertifications()) {
                if (blank(c.getName())) continue;
                sb.append("\\textbf{\\small ").append(esc(c.getName())).append("}\\par\n");
                sb.append("{\\footnotesize\\color{textgray} ").append(esc(c.getIssuer())).append("}\\par\\vspace{4pt}\n");
            }
        }
        sb.append("\\end{minipage}\\hfill\n");

        sb.append("\\begin{minipage}[t]{0.48\\textwidth}\n");
        if (hasItems(d.getSkills())) {
            sb.append(sectionHeader("Skills", "accentcolor"));
            if (d.getSkills() != null) {
                sb.append("{\\small\\color{textgray} ").append(String.join(", ", d.getSkills().stream().filter(s -> !blank(s)).map(this::esc).toList())).append("}\\par\\vspace{6pt}\n");
            }
        }
        if (hasItems(d.getProjects())) {
            sb.append(sectionHeader("Projects", "accentcolor"));
            for (ResumeDataDto.ProjectDto p : d.getProjects()) {
                if (blank(p.getName())) continue;
                sb.append("\\textbf{\\small ").append(esc(p.getName())).append("}\\par\n");
                if (notBlank(p.getDescription())) sb.append("{\\footnotesize\\color{textgray} ").append(esc(p.getDescription())).append("}\\par\n");
                sb.append("\\vspace{4pt}\n");
            }
        }
        sb.append("\\end{minipage}\n");

        sb.append("\\end{document}\n");
        return sb.toString();
    }

    // ── Template 3: Creative Crimson (Dark Sidebar) — supports photo ───────────

    private String template3DarkSidebar(ResumeDataDto d) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                \\documentclass[10pt,a4paper]{article}
                \\usepackage[utf8]{inputenc}
                \\usepackage[T1]{fontenc}
                \\usepackage[margin=0pt,top=0pt,bottom=0pt,left=0pt,right=0pt]{geometry}
                \\usepackage{xcolor}
                \\usepackage{helvet}
                \\renewcommand{\\familydefault}{\\sfdefault}
                \\usepackage{enumitem}
                \\usepackage{parskip}
                \\usepackage{microtype}
                \\usepackage{hyperref}
                \\usepackage{graphicx}
                \\usepackage{tikz}
                \\hypersetup{colorlinks=false,hidelinks}
                \\pagestyle{empty}
                \\setlength{\\parindent}{0pt}
                \\definecolor{darkbg}{HTML}{1e2d3d}
                \\definecolor{accent}{HTML}{2ecc71}
                \\definecolor{textgray}{HTML}{444444}
                \\definecolor{lighttext}{HTML}{888888}
                \\newcommand{\\sideheader}[1]{%
                  {\\fontsize{7}{9}\\bfseries\\color{accent}\\MakeUppercase{#1}}\\par
                  {\\color{accent!40!darkbg}\\rule{\\linewidth}{0.5pt}}\\par\\vspace{4pt}
                }
                \\newcommand{\\mainheader}[1]{%
                  {\\fontsize{8}{10}\\bfseries\\color{darkbg}\\MakeUppercase{#1}}\\par
                  {\\rule{\\linewidth}{0.8pt}}\\par\\vspace{4pt}
                }
                \\newcommand{\\circphoto}[2]{%
                  \\begin{tikzpicture}
                    \\begin{scope}
                      \\clip (0,0) circle (#1/2);
                      \\node[inner sep=0pt] at (0,0) {\\includegraphics[width=#1,height=#1,keepaspectratio=false]{photo}};
                    \\end{scope}
                    \\draw[#2,line width=0.6mm] (0,0) circle (#1/2);
                  \\end{tikzpicture}%
                }
                \\begin{document}
                \\noindent
                """);

        // Left dark sidebar ~32%
        sb.append("\\begin{minipage}[t][270mm][t]{0.32\\textwidth}\n");
        sb.append("\\colorbox{darkbg}{\\begin{minipage}[t][270mm][t]{\\textwidth}\\vspace{12pt}\\hspace{8pt}\n");
        sb.append("\\begin{minipage}[t]{0.88\\textwidth}\n");

        // Photo inside sidebar (centered, circular clip via radius)
        if (hasPhoto(d)) {
            sb.append("\\begin{center}\n");
            sb.append("  \\circphoto{2.4cm}{accent}\\par\\vspace{8pt}\n");
            sb.append("\\end{center}\n");
        }

        sb.append("{\\color{white}\\fontsize{13}{16}\\bfseries\\selectfont ").append(esc(d.getFullName())).append("}\\par\\vspace{3pt}\n");
        sb.append("{\\color{accent}\\fontsize{8}{10}\\bfseries\\selectfont\\MakeUppercase{").append(esc(d.getTargetJobTitle())).append("}}\\par\\vspace{10pt}\n");

        sb.append("\\sideheader{Contact}\n");
        if (notBlank(d.getEmail()))   sb.append("{\\fontsize{8}{11}\\color{white!80!darkbg}\\selectfont ").append(esc(d.getEmail())).append("}\\par\n");
        if (notBlank(d.getPhone()))   sb.append("{\\fontsize{8}{11}\\color{white!80!darkbg}\\selectfont ").append(esc(d.getPhone())).append("}\\par\n");
        if (notBlank(d.getAddress())) sb.append("{\\fontsize{8}{11}\\color{white!80!darkbg}\\selectfont ").append(esc(d.getAddress())).append("}\\par\n");
        sb.append("\\vspace{8pt}\n");

        if (hasItems(d.getSkills())) {
            sb.append("\\sideheader{Skills}\n");
            for (String s : d.getSkills()) {
                if (blank(s)) continue;
                sb.append("{\\fontsize{8}{11}\\color{white!90!darkbg}\\selectfont ").append(esc(s)).append("}\\par\\vspace{1pt}\n");
            }
            sb.append("\\vspace{8pt}\n");
        }

        if (hasItems(d.getEducation())) {
            sb.append("\\sideheader{Education}\n");
            for (ResumeDataDto.EducationDto e : d.getEducation()) {
                if (blank(e.getDegree()) && blank(e.getInstitution())) continue;
                sb.append("{\\fontsize{8.5}{11}\\bfseries\\color{white}\\selectfont ").append(esc(e.getDegree())).append("}\\par\n");
                sb.append("{\\fontsize{8}{10}\\color{accent}\\selectfont ").append(esc(e.getInstitution())).append("}\\par\n");
                sb.append("{\\fontsize{7.5}{10}\\color{white!60!darkbg}\\selectfont ").append(esc(e.getStartDate())).append("--").append(esc(e.getEndDate())).append("}\\par\n");
                if (notBlank(e.getGrade())) sb.append("{\\fontsize{7.5}{10}\\color{accent}\\selectfont GPA: ").append(esc(e.getGrade())).append("}\\par\\vspace{5pt}\n");
                else sb.append("\\vspace{5pt}\n");
            }
        }

        sb.append("\\end{minipage}\\end{minipage}}\\end{minipage}%\n");

        // Right main area ~68%
        sb.append("\\hfill\\begin{minipage}[t]{0.65\\textwidth}\\vspace{12pt}\\hspace{10pt}\n");
        sb.append("\\begin{minipage}[t]{0.92\\textwidth}\n");

        if (notBlank(d.getSummary())) {
            sb.append("\\mainheader{About Me}\n");
            sb.append("{\\fontsize{9}{13}\\selectfont\\color{textgray} ").append(esc(d.getSummary())).append("}\\par\\vspace{8pt}\n");
        }

        if (hasItems(d.getExperience())) {
            sb.append("\\mainheader{Work Experience}\n");
            for (ResumeDataDto.ExperienceDto e : d.getExperience()) {
                if (blank(e.getPosition()) && blank(e.getCompany())) continue;
                sb.append("\\textbf{\\fontsize{10}{13}\\selectfont ").append(esc(e.getPosition())).append("}\\hfill{\\small\\color{lighttext}\\itshape ").append(dateLine(e)).append("}\\par\n");
                sb.append("{\\fontsize{9}{12}\\bfseries\\color{accent}\\selectfont ").append(esc(e.getCompany())).append("}\\par\n");
                appendBullets(sb, e);
                sb.append("\\vspace{6pt}\n");
            }
        }

        if (hasItems(d.getProjects())) {
            sb.append("\\mainheader{Projects}\n");
            for (ResumeDataDto.ProjectDto p : d.getProjects()) {
                if (blank(p.getName())) continue;
                String tech = notBlank(p.getTechStack()) ? p.getTechStack() : p.getTechnologies();
                sb.append("\\textbf{\\small ").append(esc(p.getName())).append("}");
                if (notBlank(tech)) sb.append(" --- {\\footnotesize\\color{accent} ").append(esc(tech)).append("}");
                sb.append("\\par\n");
                if (notBlank(p.getDescription())) sb.append("{\\footnotesize\\color{textgray} ").append(esc(p.getDescription())).append("}\\par\n");
                sb.append("\\vspace{4pt}\n");
            }
        }

        if (hasItems(d.getCertifications())) {
            sb.append("\\mainheader{Certifications}\n");
            for (ResumeDataDto.CertificationDto c : d.getCertifications()) {
                if (blank(c.getName())) continue;
                sb.append("\\textbf{\\small ").append(esc(c.getName())).append("}\\par\n");
                String yr = notBlank(c.getDate()) ? c.getDate() : (notBlank(c.getYear()) ? c.getYear() : "");
                sb.append("{\\footnotesize\\color{textgray} ").append(esc(c.getIssuer())).append(notBlank(yr) ? " $\\cdot$ " + esc(yr) : "").append("}\\par\\vspace{4pt}\n");
            }
        }

        sb.append("\\end{minipage}\\end{minipage}\n");
        sb.append("\\end{document}\n");
        return sb.toString();
    }

    // ── Template 4: Minimalist Ivory (Purple Executive) — supports photo ───────

    private String template4PurpleExecutive(ResumeDataDto d) {
        StringBuilder sb = new StringBuilder();
        sb.append(latexPreamble("7c3aed", "helvet", hasPhoto(d)));
        sb.append("\\definecolor{accent}{HTML}{7c3aed}\n\\definecolor{accentdark}{HTML}{4f46e5}\n");
        sb.append(latexDocStart());

        // Header: photo right, text left
        sb.append("\\colorbox{accent}{%\n\\begin{minipage}{\\textwidth}\\vspace{14pt}\\hspace{16pt}\n");
        if (hasPhoto(d)) {
            sb.append("\\begin{minipage}[c]{0.78\\textwidth}\n");
        } else {
            sb.append("\\begin{minipage}{0.88\\textwidth}\n");
        }
        sb.append("{\\color{white}\\fontsize{22}{26}\\bfseries\\selectfont ").append(esc(d.getFullName())).append("}\\par\\vspace{3pt}\n");
        sb.append("{\\color{white!80!accent}\\fontsize{10}{13}\\selectfont ").append(esc(d.getTargetJobTitle())).append("}\\par\\vspace{8pt}\n");
        sb.append("{\\color{white!70!accent}\\footnotesize ").append(contactLine(d, " \\quad ")).append("}\n");
        sb.append("\\end{minipage}");
        if (hasPhoto(d)) {
            sb.append("\\hfill\\begin{minipage}[c]{0.15\\textwidth}\n");
            sb.append("  \\begin{tikzpicture}\n");
            sb.append("    \\clip[rounded corners=3mm] (-1.1,-1.1) rectangle (1.1,1.1);\n");
            sb.append("    \\node[inner sep=0pt] at (0,0) {\\includegraphics[width=2.2cm,height=2.2cm,keepaspectratio=false]{photo}};\n");
            sb.append("    \\draw[white!50!accent,line width=0.6mm,rounded corners=3mm] (-1.1,-1.1) rectangle (1.1,1.1);\n");
            sb.append("  \\end{tikzpicture}\n");
            sb.append("\\end{minipage}");
        }
        sb.append("\\vspace{14pt}\\end{minipage}}\n\\par\\vspace{8pt}\n");

        if (notBlank(d.getSummary())) {
            sb.append("\\colorbox{accent!5}{\\begin{minipage}{\\textwidth}\\vspace{6pt}\\hspace{8pt}\n");
            sb.append("\\begin{minipage}{0.96\\textwidth}\n");
            sb.append("{\\fontsize{7.5}{10}\\bfseries\\color{accent}\\MakeUppercase{Professional Summary}}\\par\\vspace{3pt}\n");
            sb.append("{\\fontsize{9.5}{14}\\selectfont\\color{textgray} ").append(esc(d.getSummary())).append("}\\vspace{6pt}\n");
            sb.append("\\end{minipage}\\end{minipage}}\\par\\vspace{8pt}\n");
        }

        if (hasItems(d.getExperience())) {
            sb.append(sectionHeader("Work Experience", "accent"));
            for (ResumeDataDto.ExperienceDto e : d.getExperience()) {
                if (blank(e.getPosition()) && blank(e.getCompany())) continue;
                sb.append("\\colorbox{gray!8}{\\begin{minipage}{\\textwidth}\\vspace{4pt}\\hspace{6pt}\n");
                sb.append("\\begin{minipage}{0.95\\textwidth}\n");
                sb.append("\\textbf{\\fontsize{10}{13}\\selectfont ").append(esc(e.getPosition())).append("}\\hfill{\\small\\color{lighttext}\\itshape ").append(dateLine(e)).append("}\\par\n");
                sb.append("{\\fontsize{9}{12}\\bfseries\\color{accent}\\selectfont ").append(esc(e.getCompany())).append("}\\par\n");
                appendBullets(sb, e);
                sb.append("\\end{minipage}\\vspace{4pt}\\end{minipage}}\\par\\vspace{4pt}\n");
            }
        }

        // Two-column lower section
        sb.append("\\begin{minipage}[t]{0.48\\textwidth}\n");
        if (hasItems(d.getEducation())) {
            sb.append(sectionHeader("Education", "accent"));
            for (ResumeDataDto.EducationDto e : d.getEducation()) {
                if (blank(e.getDegree()) && blank(e.getInstitution())) continue;
                sb.append("\\textbf{\\small ").append(esc(e.getDegree())).append("}\\par\n");
                if (notBlank(e.getField())) sb.append("{\\footnotesize\\color{textgray} ").append(esc(e.getField())).append("}\\par\n");
                sb.append("{\\small\\bfseries\\color{accent} ").append(esc(e.getInstitution())).append("}\\par\n");
                sb.append("{\\footnotesize\\color{lighttext} ").append(esc(e.getStartDate())).append("--").append(esc(e.getEndDate())).append("}\\par\n");
                if (notBlank(e.getGrade())) sb.append("{\\footnotesize\\color{lighttext} GPA/Grade: ").append(esc(e.getGrade())).append("}\\par\\vspace{5pt}\n");
                else sb.append("\\vspace{5pt}\n");
            }
        }
        if (hasItems(d.getCertifications())) {
            sb.append(sectionHeader("Certifications", "accent"));
            for (ResumeDataDto.CertificationDto c : d.getCertifications()) {
                if (blank(c.getName())) continue;
                sb.append("\\textbf{\\small ").append(esc(c.getName())).append("}\\par\n");
                sb.append("{\\footnotesize\\color{textgray} ").append(esc(c.getIssuer())).append("}\\par\\vspace{4pt}\n");
            }
        }
        sb.append("\\end{minipage}\\hfill\n");

        sb.append("\\begin{minipage}[t]{0.48\\textwidth}\n");
        if (hasItems(d.getSkills())) {
            sb.append(sectionHeader("Skills", "accent"));
            if (d.getSkills() != null) {
                for (String s : d.getSkills()) {
                    if (blank(s)) continue;
                    sb.append("\\colorbox{accent!10}{\\textcolor{accent}{\\fontsize{8}{10}\\selectfont\\strut ").append(esc(s)).append("}} ");
                }
                sb.append("\\par\\vspace{6pt}\n");
            }
        }
        if (hasItems(d.getProjects())) {
            sb.append(sectionHeader("Projects", "accent"));
            for (ResumeDataDto.ProjectDto p : d.getProjects()) {
                if (blank(p.getName())) continue;
                sb.append("\\textbf{\\small ").append(esc(p.getName())).append("}\\par\n");
                String tech = notBlank(p.getTechStack()) ? p.getTechStack() : p.getTechnologies();
                if (notBlank(tech)) sb.append("{\\footnotesize\\color{accent} ").append(esc(tech)).append("}\\par\n");
                if (notBlank(p.getDescription())) sb.append("{\\footnotesize\\color{textgray} ").append(esc(p.getDescription())).append("}\\par\n");
                sb.append("\\vspace{4pt}\n");
            }
        }
        sb.append("\\end{minipage}\n");

        sb.append("\\end{document}\n");
        return sb.toString();
    }

    // ── Template 5: ATS Optimised Pro (Bold Red) — no photo ───────────────────

    private String template5BoldRed(ResumeDataDto d) {
        StringBuilder sb = new StringBuilder();
        sb.append(latexPreamble("e63946", "helvet", false));
        sb.append("\\definecolor{accent}{HTML}{e63946}\n");
        sb.append(latexDocStart());

        sb.append("{\\color{accent}\\rule{\\textwidth}{3pt}}\\par\\vspace{6pt}\n");
        sb.append("{\\fontsize{24}{28}\\bfseries\\MakeUppercase{").append(esc(d.getFullName())).append("}}\\par\\vspace{3pt}\n");
        sb.append("{\\fontsize{10}{13}\\bfseries\\color{accent}\\MakeUppercase{").append(esc(d.getTargetJobTitle())).append("}}\\par\\vspace{5pt}\n");
        sb.append("{\\small\\color{textgray} ").append(contactLine(d, " | ")).append("}\\par\\vspace{6pt}\n");

        if (hasItems(d.getSkills())) {
            sb.append("\\colorbox{black!85}{\\begin{minipage}{\\textwidth}\\vspace{3pt}\\hspace{8pt}\n");
            sb.append("\\begin{minipage}{0.97\\textwidth}\\fontsize{8}{10}\\bfseries\\color{accent}\\selectfont\\MakeUppercase{");
            if (d.getSkills() != null) {
                sb.append(String.join(" \\quad ", d.getSkills().stream().filter(s -> !blank(s)).map(this::esc).toList()));
            }
            sb.append("}\\end{minipage}\\vspace{3pt}\\end{minipage}}\\par\\vspace{8pt}\n");
        }

        if (notBlank(d.getSummary())) {
            sb.append(sectionHeader("Profile", "accent"));
            sb.append("{\\fontsize{9.5}{14}\\selectfont\\color{textgray} ").append(esc(d.getSummary())).append("}\\par\\vspace{8pt}\n");
        }

        if (hasItems(d.getExperience())) {
            sb.append(sectionHeader("Experience", "accent"));
            for (ResumeDataDto.ExperienceDto e : d.getExperience()) {
                if (blank(e.getPosition()) && blank(e.getCompany())) continue;
                sb.append("\\textbf{\\fontsize{10}{13}\\selectfont ").append(esc(e.getPosition())).append("}\\hfill{\\small\\color{lighttext}\\itshape ").append(dateLine(e)).append("}\\par\n");
                sb.append("{\\fontsize{9}{12}\\bfseries\\color{accent}\\selectfont ").append(esc(e.getCompany())).append("}\\par\n");
                appendBullets(sb, e);
                sb.append("\\vspace{6pt}\n");
            }
        }

        sb.append("\\begin{minipage}[t]{0.58\\textwidth}\n");
        if (hasItems(d.getProjects())) {
            sb.append(sectionHeader("Projects", "accent"));
            for (ResumeDataDto.ProjectDto p : d.getProjects()) {
                if (blank(p.getName())) continue;
                sb.append("\\textbf{\\small ").append(esc(p.getName())).append("}\\par\n");
                String tech = notBlank(p.getTechStack()) ? p.getTechStack() : p.getTechnologies();
                if (notBlank(tech)) sb.append("{\\footnotesize\\color{accent} ").append(esc(tech)).append("}\\par\n");
                if (notBlank(p.getDescription())) sb.append("{\\footnotesize\\color{textgray} ").append(esc(p.getDescription())).append("}\\par\n");
                sb.append("\\vspace{4pt}\n");
            }
        }
        sb.append("\\end{minipage}\\hfill\n");

        sb.append("\\begin{minipage}[t]{0.38\\textwidth}\n");
        if (hasItems(d.getEducation())) {
            sb.append(sectionHeader("Education", "accent"));
            for (ResumeDataDto.EducationDto e : d.getEducation()) {
                if (blank(e.getDegree()) && blank(e.getInstitution())) continue;
                sb.append("\\textbf{\\small ").append(esc(e.getDegree())).append("}\\par\n");
                sb.append("{\\small\\color{accent} ").append(esc(e.getInstitution())).append("}\\par\n");
                sb.append("{\\footnotesize\\color{lighttext} ").append(esc(e.getStartDate())).append("--").append(esc(e.getEndDate())).append("}\\par\n");
                if (notBlank(e.getGrade())) sb.append("{\\footnotesize\\color{accent} GPA: ").append(esc(e.getGrade())).append("}\\par\\vspace{4pt}\n");
                else sb.append("\\vspace{4pt}\n");
            }
        }
        if (hasItems(d.getCertifications())) {
            sb.append(sectionHeader("Certifications", "accent"));
            for (ResumeDataDto.CertificationDto c : d.getCertifications()) {
                if (blank(c.getName())) continue;
                sb.append("\\textbf{\\small ").append(esc(c.getName())).append("}\\par\n");
                sb.append("{\\footnotesize\\color{textgray} ").append(esc(c.getIssuer())).append("}\\par\\vspace{4pt}\n");
            }
        }
        sb.append("\\end{minipage}\n");

        sb.append("\\end{document}\n");
        return sb.toString();
    }

    // ── Template 6: Sapphire Split (ATS Clean Blue) — no photo ────────────────

    private String template6ATSBlue(ResumeDataDto d) {
        StringBuilder sb = new StringBuilder();
        sb.append(latexPreamble("0070f3", "helvet", false));
        sb.append("\\definecolor{accent}{HTML}{0070f3}\n");
        sb.append(latexDocStart());

        sb.append("{\\fontsize{22}{26}\\bfseries\\color{black!90}\\selectfont ").append(esc(d.getFullName())).append("}\\par\\vspace{3pt}\n");
        sb.append("{\\fontsize{11}{14}\\color{accent}\\bfseries\\selectfont ").append(esc(d.getTargetJobTitle())).append("}\\par\\vspace{4pt}\n");
        sb.append("{\\color{accent}\\rule{\\textwidth}{1.5pt}}\\par\\vspace{4pt}\n");
        sb.append("{\\small\\color{textgray} ").append(contactLine(d, " \\textbullet\\ ")).append("}\\par\\vspace{10pt}\n");

        if (notBlank(d.getSummary())) {
            sb.append("\\colorbox{accent!5}{\\begin{minipage}{\\textwidth}\\vspace{5pt}\\hspace{8pt}\n");
            sb.append("\\begin{minipage}{0.97\\textwidth}\n");
            sb.append("{\\fontsize{7.5}{10}\\bfseries\\color{accent}\\MakeUppercase{Summary}}\\par\\vspace{3pt}\n");
            sb.append("{\\fontsize{9.5}{14}\\selectfont\\color{textgray} ").append(esc(d.getSummary())).append("}\\vspace{5pt}\n");
            sb.append("\\end{minipage}\\end{minipage}}\\par\\vspace{8pt}\n");
        }

        if (hasItems(d.getSkills())) {
            sb.append(sectionHeader("Core Skills", "accent"));
            sb.append("\\colorbox{gray!8}{\\begin{minipage}{\\textwidth}\\vspace{6pt}\\hspace{8pt}\n");
            sb.append("\\begin{minipage}{0.97\\textwidth}\\fontsize{9}{12}\\selectfont ");
            if (d.getSkills() != null) {
                for (String s : d.getSkills()) {
                    if (blank(s)) continue;
                    sb.append("\\colorbox{white}{\\textcolor{accent}{\\strut ").append(esc(s)).append("}} ");
                }
            }
            sb.append("\\end{minipage}\\vspace{6pt}\\end{minipage}}\\par\\vspace{8pt}\n");
        }

        if (hasItems(d.getExperience())) {
            sb.append(sectionHeader("Work Experience", "accent"));
            for (ResumeDataDto.ExperienceDto e : d.getExperience()) {
                if (blank(e.getPosition()) && blank(e.getCompany())) continue;
                sb.append("\\colorbox{gray!7}{\\begin{minipage}{\\textwidth}\\vspace{4pt}\\hspace{6pt}\n");
                sb.append("\\begin{minipage}{0.96\\textwidth}\n");
                sb.append("\\textbf{\\fontsize{10}{13}\\selectfont ").append(esc(e.getPosition())).append("}\\hfill{\\small\\color{lighttext}\\itshape ").append(dateLine(e)).append("}\\par\n");
                sb.append("{\\fontsize{9}{12}\\bfseries\\color{accent}\\selectfont ").append(esc(e.getCompany())).append("}\\par\n");
                appendBullets(sb, e);
                sb.append("\\end{minipage}\\vspace{4pt}\\end{minipage}}\\par\\vspace{4pt}\n");
            }
        }

        sb.append("\\begin{minipage}[t]{0.48\\textwidth}\n");
        if (hasItems(d.getEducation())) {
            sb.append(sectionHeader("Education", "accent"));
            for (ResumeDataDto.EducationDto e : d.getEducation()) {
                if (blank(e.getDegree()) && blank(e.getInstitution())) continue;
                sb.append("\\colorbox{gray!7}{\\begin{minipage}{\\textwidth}\\vspace{4pt}\\hspace{4pt}\n");
                sb.append("\\begin{minipage}{0.95\\textwidth}\n");
                sb.append("\\textbf{\\small ").append(esc(e.getDegree())).append("}\\par\n");
                if (notBlank(e.getField())) sb.append("{\\footnotesize\\color{textgray} ").append(esc(e.getField())).append("}\\par\n");
                sb.append("{\\small\\bfseries\\color{accent} ").append(esc(e.getInstitution())).append("}\\par\n");
                sb.append("{\\footnotesize\\color{lighttext} ").append(esc(e.getStartDate())).append("--").append(esc(e.getEndDate())).append("}\\par\n");
                if (notBlank(e.getGrade())) sb.append("{\\footnotesize\\color{accent} GPA/Grade: ").append(esc(e.getGrade())).append("}\\par\n");
                sb.append("\\end{minipage}\\vspace{4pt}\\end{minipage}}\\par\\vspace{4pt}\n");
            }
        }
        sb.append("\\end{minipage}\\hfill\n");

        sb.append("\\begin{minipage}[t]{0.48\\textwidth}\n");
        if (hasItems(d.getProjects())) {
            sb.append(sectionHeader("Projects", "accent"));
            for (ResumeDataDto.ProjectDto p : d.getProjects()) {
                if (blank(p.getName())) continue;
                sb.append("\\colorbox{gray!7}{\\begin{minipage}{\\textwidth}\\vspace{4pt}\\hspace{4pt}\n");
                sb.append("\\begin{minipage}{0.95\\textwidth}\n");
                sb.append("\\textbf{\\small ").append(esc(p.getName())).append("}\\par\n");
                String tech = notBlank(p.getTechStack()) ? p.getTechStack() : p.getTechnologies();
                if (notBlank(tech)) sb.append("{\\footnotesize\\color{accent} ").append(esc(tech)).append("}\\par\n");
                if (notBlank(p.getDescription())) sb.append("{\\footnotesize\\color{textgray} ").append(esc(p.getDescription())).append("}\\par\n");
                sb.append("\\end{minipage}\\vspace{4pt}\\end{minipage}}\\par\\vspace{4pt}\n");
            }
        }
        if (hasItems(d.getCertifications())) {
            sb.append(sectionHeader("Certifications", "accent"));
            for (ResumeDataDto.CertificationDto c : d.getCertifications()) {
                if (blank(c.getName())) continue;
                sb.append("\\textbf{\\small ").append(esc(c.getName())).append("}\\par\n");
                sb.append("{\\footnotesize\\color{textgray} ").append(esc(c.getIssuer())).append("}\\par\\vspace{4pt}\n");
            }
        }
        sb.append("\\end{minipage}\n");

        sb.append("\\end{document}\n");
        return sb.toString();
    }

    // ── LaTeX compilation ──────────────────────────────────────────────────────

    /**
     * Compiles the given LaTeX string to PDF bytes.
     * If {@code photoBase64} is non-blank the photo is written to
     * {@code <tmpDir>/photo.png} so that LaTeX can locate it via
     * {@code \includegraphics{photo}}.
     */
    private byte[] compilePdf(String latex, String photoBase64) throws Exception {
        Path tmpDir  = Files.createTempDirectory("resume-latex-" + UUID.randomUUID().toString().substring(0, 8));
        Path texFile = tmpDir.resolve("resume.tex");
        Path pdfFile = tmpDir.resolve("resume.pdf");

        try {
            Files.writeString(texFile, latex);

            // Write photo image to temp dir if provided
            if (notBlank(photoBase64)) {
                byte[] imgBytes = Base64.getDecoder().decode(
                        photoBase64.replaceAll("^data:image/[^;]+;base64,", ""));
                Files.write(tmpDir.resolve("photo.png"), imgBytes);
            }

            ProcessBuilder pb = new ProcessBuilder(
                    "pdflatex",
                    "-interaction=nonstopmode",
                    "-output-directory=" + tmpDir,
                    texFile.toString()
            );
            pb.directory(tmpDir.toFile());
            pb.redirectErrorStream(true);

            // Run twice for stable cross-references and layout
            String output = runProcess(pb);
            if (Files.exists(pdfFile)) runProcess(pb); // second pass

            if (!Files.exists(pdfFile)) {
                log.error("LaTeX compilation failed:\n{}", output);
                throw new RuntimeException("LaTeX compilation failed — pdflatex produced no output.");
            }

            return Files.readAllBytes(pdfFile);
        } finally {
            deleteDirQuietly(tmpDir);
        }
    }

    private String runProcess(ProcessBuilder pb) throws Exception {
        Process proc    = pb.start();
        String  output  = new String(proc.getInputStream().readAllBytes());
        int     exit    = proc.waitFor();
        if (exit != 0) log.warn("pdflatex exit={} output={}", exit, output);
        return output;
    }

    private void deleteDirQuietly(Path dir) {
        try {
            Files.walk(dir)
                 .sorted(java.util.Comparator.reverseOrder())
                 .map(Path::toFile)
                 .forEach(File::delete);
        } catch (Exception ignored) {}
    }

    // ── Shared LaTeX helpers ───────────────────────────────────────────────────

    /**
     * Standard preamble.
     *
     * @param accentHex  6-digit hex color (no #)
     * @param fontPkg    "palatino" or "helvet"
     * @param needsPhoto whether to include the graphicx package
     */
    private String latexPreamble(String accentHex, String fontPkg, boolean needsPhoto) {
        String fontCmd = fontPkg.equals("palatino")
                ? "\\usepackage{palatino}\n"
                : "\\usepackage{helvet}\n\\renewcommand{\\familydefault}{\\sfdefault}\n";
        String graphicsCmd = needsPhoto
                ? "\\usepackage{graphicx}\n\\usepackage{tikz}\n"
                : "";
        // \circphoto{<width>}{<border-color>} — draws a circular‐clipped photo with border
        String circPhotoCmd = needsPhoto
                ? """
                \\newcommand{\\circphoto}[2]{%
                  \\begin{tikzpicture}
                    \\begin{scope}
                      \\clip (0,0) circle (#1/2);
                      \\node[inner sep=0pt] at (0,0) {\\includegraphics[width=#1,height=#1,keepaspectratio=false]{photo}};
                    \\end{scope}
                    \\draw[#2,line width=0.6mm] (0,0) circle (#1/2);
                  \\end{tikzpicture}%
                }
                """
                : "";
        return """
                \\documentclass[10pt,a4paper]{article}
                \\usepackage[utf8]{inputenc}
                \\usepackage[T1]{fontenc}
                \\usepackage[top=0pt,bottom=1.2cm,left=1.5cm,right=1.5cm]{geometry}
                \\usepackage{xcolor}
                """ + fontCmd + graphicsCmd + """
                \\usepackage{enumitem}
                \\usepackage{parskip}
                \\usepackage{microtype}
                \\usepackage{hyperref}
                \\usepackage{tabularx}
                \\hypersetup{colorlinks=false,hidelinks}
                \\pagestyle{empty}
                \\setlength{\\parindent}{0pt}
                \\setlength{\\parskip}{0pt}
                \\definecolor{textgray}{HTML}{444444}
                \\definecolor{lighttext}{HTML}{888888}
                \\setlist[itemize]{leftmargin=1.2em,itemsep=1pt,topsep=2pt,parsep=0pt}
                """ + circPhotoCmd;
    }

    private String latexDocStart() {
        return "\\begin{document}\n";
    }

    private String sectionHeader(String title, String colorName) {
        return "{\\fontsize{8}{10}\\bfseries\\color{" + colorName + "}\\MakeUppercase{" + title + "}}\\par\\vspace{-3pt}\n" +
               "{\\color{" + colorName + "}\\rule{\\linewidth}{0.8pt}}\\par\\vspace{5pt}\n";
    }

    private String contactLine(ResumeDataDto d, String sep) {
        StringBuilder sb = new StringBuilder();
        if (notBlank(d.getEmail()))       appendContact(sb, esc(d.getEmail()), sep);
        if (notBlank(d.getPhone()))       appendContact(sb, esc(d.getPhone()), sep);
        if (notBlank(d.getAddress()))     appendContact(sb, esc(d.getAddress()), sep);
        if (notBlank(d.getLinkedinUrl())) appendContact(sb, esc(d.getLinkedinUrl()), sep);
        if (notBlank(d.getGithubUrl()))   appendContact(sb, esc(d.getGithubUrl()), sep);
        return sb.toString();
    }

    private void appendContact(StringBuilder sb, String val, String sep) {
        if (!sb.isEmpty()) sb.append(sep);
        sb.append(val);
    }

    private String dateLine(ResumeDataDto.ExperienceDto e) {
        String start = notBlank(e.getStartDate()) ? e.getStartDate() : "";
        String end   = e.isCurrent() ? "Present" : (notBlank(e.getEndDate()) ? e.getEndDate() : "");
        if (notBlank(start) && notBlank(end)) return esc(start) + "--" + esc(end);
        if (notBlank(start)) return esc(start);
        if (notBlank(end))   return esc(end);
        return "";
    }

    private void appendBullets(StringBuilder sb, ResumeDataDto.ExperienceDto e) {
        List<String> bullets = e.getBullets();
        boolean hasBullets   = bullets != null && !bullets.isEmpty()
                               && bullets.stream().anyMatch(b -> !blank(b));

        if (hasBullets) {
            sb.append("\\begin{itemize}\n");
            for (String b : bullets) {
                if (blank(b)) continue;
                sb.append("  \\item ").append(esc(b.replaceAll("^[•\\-*]\\s*", ""))).append("\n");
            }
            sb.append("\\end{itemize}\n");
        } else if (notBlank(e.getDescription())) {
            String[] lines = e.getDescription().split("\n");
            boolean any = java.util.Arrays.stream(lines)
                    .map(String::trim).map(l -> l.replaceAll("^[•\\-*]\\s*", ""))
                    .anyMatch(l -> !l.isEmpty());
            if (any) {
                sb.append("\\begin{itemize}\n");
                for (String line : lines) {
                    String t = line.trim().replaceAll("^[•\\-*]\\s*", "");
                    if (t.isEmpty()) continue;
                    sb.append("  \\item ").append(esc(t)).append("\n");
                }
                sb.append("\\end{itemize}\n");
            }
        }
    }

    /** Escape all LaTeX special characters. */
    private String esc(String s) {
        if (s == null) return "";
        return s
            .replace("\\", "\\textbackslash{}")
            .replace("&",  "\\&")
            .replace("%",  "\\%")
            .replace("$",  "\\$")
            .replace("#",  "\\#")
            .replace("_",  "\\_")
            .replace("{",  "\\{")
            .replace("}",  "\\}")
            .replace("~",  "\\textasciitilde{}")
            .replace("^",  "\\textasciicircum{}")
            .replace(">",  "\\textgreater{}")
            .replace("<",  "\\textless{}")
            .replace("|",  "\\textbar{}")
            .replace("\u2013", "--")
            .replace("\u2014", "---")
            .replace("\u2022", "\\textbullet{}")
            .replace("\u00e9", "\\'e")
            .replace("\u00e8", "\\`e")
            .replace("\u00ea", "\\^e")
            .replace("\u00e0", "\\`a")
            .replace("\u00e2", "\\^a")
            .replace("\u00fc", "\\\"u")
            .replace("\u00f6", "\\\"o")
            .replace("\u00e4", "\\\"a");
    }

    private boolean hasPhoto(ResumeDataDto d) {
        return d != null && notBlank(d.getPhotoBase64());
    }

    private boolean blank(String s)    { return s == null || s.isBlank(); }
    private boolean notBlank(String s) { return s != null && !s.isBlank(); }
    private boolean hasItems(List<?> l){ return l != null && !l.isEmpty(); }
}
