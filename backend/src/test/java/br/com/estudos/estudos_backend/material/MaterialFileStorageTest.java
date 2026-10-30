package br.com.estudos.estudos_backend.material;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialFileStorageTest {

	@TempDir
	Path tempDir;

	private MaterialFileStorage storage;

	@BeforeEach
	void setUp() {
		storage = new MaterialFileStorage(tempDir.toString());
	}

	@Test
	void extractsTextFromPlainTxtFile() {
		String text = "Conteúdo didático em texto puro sobre Java 21.";
		MockMultipartFile file = new MockMultipartFile(
				"file", "estudo.txt", "text/plain", text.getBytes(StandardCharsets.UTF_8));

		String extracted = storage.readAndStore(file);

		assertEquals(text, extracted);
	}

	@Test
	void extractsTextFromMarkdownFileAndStripsBom() {
		String rawMarkdown = "\uFEFF# Introdução ao Spring Boot\n\nFramework para microsserviços modernos.";
		MockMultipartFile file = new MockMultipartFile(
				"file", "resumo.md", "text/markdown", rawMarkdown.getBytes(StandardCharsets.UTF_8));

		String extracted = storage.readAndStore(file);

		assertFalse(extracted.startsWith("\uFEFF"));
		assertTrue(extracted.contains("# Introdução ao Spring Boot"));
		assertTrue(extracted.contains("Framework para microsserviços modernos."));
	}

	@Test
	void extractsTextFromValidPdfDocument() throws IOException {
		byte[] pdfBytes;
		try (PDDocument doc = new PDDocument()) {
			PDPage page = new PDPage();
			doc.addPage(page);
			try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
				cs.beginText();
				cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
				cs.showText("Arquitetura Hexagonal com Spring Boot.");
				cs.endText();
			}
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			doc.save(out);
			pdfBytes = out.toByteArray();
		}

		MockMultipartFile file = new MockMultipartFile(
				"file", "apostila.pdf", "application/pdf", pdfBytes);

		String extracted = storage.readAndStore(file);

		assertNotNull(extracted);
		assertTrue(extracted.contains("Arquitetura Hexagonal com Spring Boot."));
	}

	@Test
	void extractsTextFromValidDocxArchive() throws IOException {
		String docxXml = """
				<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
				<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
				  <w:body>
				    <w:p><w:r><w:t>Primeiro parágrafo de estudos sobre algoritmos.</w:t></w:r></w:p>
				    <w:p><w:r><w:t>Segundo parágrafo detalhando complexidade Big-O.</w:t></w:r></w:p>
				  </w:body>
				</w:document>
				""";

		ByteArrayOutputStream zipOut = new ByteArrayOutputStream();
		try (ZipOutputStream zos = new ZipOutputStream(zipOut)) {
			ZipEntry entry = new ZipEntry("word/document.xml");
			zos.putNextEntry(entry);
			zos.write(docxXml.getBytes(StandardCharsets.UTF_8));
			zos.closeEntry();
		}

		MockMultipartFile file = new MockMultipartFile(
				"file", "algoritmos.docx",
				"application/vnd.openxmlformats-officedocument.wordprocessingml.document",
				zipOut.toByteArray());

		String extracted = storage.readAndStore(file);

		assertNotNull(extracted);
		assertTrue(extracted.contains("Primeiro parágrafo de estudos sobre algoritmos."));
		assertTrue(extracted.contains("Segundo parágrafo detalhando complexidade Big-O."));
	}

	@Test
	void rejectsEmptyFileWithBadRequest() {
		MockMultipartFile emptyFile = new MockMultipartFile(
				"file", "vazio.txt", "text/plain", new byte[0]);

		ResponseStatusException ex = assertThrows(
				ResponseStatusException.class,
				() -> storage.readAndStore(emptyFile));

		assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
	}

	@Test
	void rejectsUnsupportedFileTypeWithUnsupportedMediaType() {
		MockMultipartFile exeFile = new MockMultipartFile(
				"file", "malware.exe", "application/octet-stream", "MZ...".getBytes(StandardCharsets.UTF_8));

		ResponseStatusException ex = assertThrows(
				ResponseStatusException.class,
				() -> storage.readAndStore(exeFile));

		assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getStatusCode());
	}

	@Test
	void rejectsOversizedTextFileWithContentTooLarge() {
		// Limite para arquivos de texto é 2MB (2_000_000 bytes)
		byte[] largeBytes = new byte[2_000_001];
		MockMultipartFile largeFile = new MockMultipartFile(
				"file", "gigante.txt", "text/plain", largeBytes);

		ResponseStatusException ex = assertThrows(
				ResponseStatusException.class,
				() -> storage.readAndStore(largeFile));

		assertEquals(HttpStatus.CONTENT_TOO_LARGE, ex.getStatusCode());
	}

	@Test
	void rejectsCorruptedPdfWithBadRequest() {
		byte[] corruptedBytes = "ISTO NÃO É UM PDF VÁLIDO".getBytes(StandardCharsets.UTF_8);
		MockMultipartFile corruptedPdf = new MockMultipartFile(
				"file", "corrupted.pdf", "application/pdf", corruptedBytes);

		ResponseStatusException ex = assertThrows(
				ResponseStatusException.class,
				() -> storage.readAndStore(corruptedPdf));

		assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
	}

	@Test
	void rejectsCorruptedDocxWithBadRequest() {
		byte[] corruptedBytes = "ISTO NÃO É UM ZIP DOCX VÁLIDO".getBytes(StandardCharsets.UTF_8);
		MockMultipartFile corruptedDocx = new MockMultipartFile(
				"file", "corrupted.docx",
				"application/vnd.openxmlformats-officedocument.wordprocessingml.document",
				corruptedBytes);

		ResponseStatusException ex = assertThrows(
				ResponseStatusException.class,
				() -> storage.readAndStore(corruptedDocx));

		assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
	}

	@Test
	void sanitizesExcessiveWhitespaceControlCharsAndBlankLines() {
		String dirty = "Linha 1\u0000 com null byte.\r\n\r\n\r\n\r\nLinha 2    com    muitos    espaços.\fLinha 3 após formfeed.";
		String cleaned = storage.sanitizeExtractedText(dirty);

		assertFalse(cleaned.contains("\u0000"));
		assertFalse(cleaned.contains("\f"));
		assertFalse(cleaned.contains("\r"));
		assertFalse(cleaned.contains("    "));
		assertFalse(cleaned.contains("\n\n\n"));
		assertTrue(cleaned.contains("Linha 1 com null byte."));
		assertTrue(cleaned.contains("Linha 2 com muitos espaços."));
		assertTrue(cleaned.contains("Linha 3 após formfeed."));
	}
}
