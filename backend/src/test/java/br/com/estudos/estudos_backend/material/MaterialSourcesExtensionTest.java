package br.com.estudos.estudos_backend.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** PPTX, código-fonte, segurança da importação web e resumo extrativo (Etapa H). */
class MaterialSourcesExtensionTest {

	@TempDir
	Path tempDir;

	private MaterialFileStorage storage() {
		return new MaterialFileStorage(tempDir.toString());
	}

	private static byte[] pptx(String... entriesAndXml) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(out)) {
			for (int i = 0; i < entriesAndXml.length; i += 2) {
				zip.putNextEntry(new ZipEntry(entriesAndXml[i]));
				zip.write(entriesAndXml[i + 1].getBytes(StandardCharsets.UTF_8));
				zip.closeEntry();
			}
		}
		return out.toByteArray();
	}

	private static String slideXml(String... paragraphs) {
		StringBuilder sb = new StringBuilder("<p:sld xmlns:p=\"p\" xmlns:a=\"a\"><p:cSld><p:spTree>");
		for (String paragraph : paragraphs) {
			sb.append("<a:p><a:r><a:t>").append(paragraph).append("</a:t></a:r></a:p>");
		}
		return sb.append("</p:spTree></p:cSld></p:sld>").toString();
	}

	@Test
	void extractsPptxSlidesInNumericOrderWithNotes() throws Exception {
		byte[] file = pptx(
				"ppt/slides/slide10.xml", slideXml("Conclusão do curso"),
				"ppt/slides/slide2.xml", slideXml("Polimorfismo", "Objetos tratados pela superclasse"),
				"ppt/notesSlides/notesSlide2.xml", slideXml("Explicar com exemplo de Forma", "2"),
				"ppt/presentation.xml", "<p:presentation xmlns:p=\"p\"/>");

		String text = storage().readAndStore(new MockMultipartFile("file", "aula.pptx",
				"application/vnd.openxmlformats-officedocument.presentationml.presentation", file));

		assertTrue(text.indexOf("Slide 2") < text.indexOf("Slide 10"), "slides devem seguir a ordem numérica");
		assertTrue(text.contains("Objetos tratados pela superclasse"));
		assertTrue(text.contains("Anotações: Explicar com exemplo de Forma"));
		assertFalse(text.contains("Anotações: Explicar com exemplo de Forma\n2"), "número do slide nas anotações é removido");
		assertEquals(StudyMaterialSourceType.FILE_PPTX, storage().sourceTypeFor("aula.pptx"));
	}

	@Test
	void rejectsPptxWithDoctypeAndWithoutSlides() throws Exception {
		byte[] xxe = pptx("ppt/slides/slide1.xml",
				"<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><a:p xmlns:a=\"a\">&e;</a:p>");
		assertThrows(ResponseStatusException.class, () -> storage().readAndStore(
				new MockMultipartFile("file", "x.pptx", "application/octet-stream", xxe)));

		byte[] empty = pptx("ppt/presentation.xml", "<p/>");
		assertThrows(ResponseStatusException.class, () -> storage().readAndStore(
				new MockMultipartFile("file", "vazio.pptx", "application/octet-stream", empty)));
	}

	@Test
	void keepsCodeIndentation() {
		String code = "public class A {\n    void run() {\n\t\treturn;\n    }\n}\n";
		String text = storage().readAndStore(new MockMultipartFile("file", "A.java", "text/plain",
				code.getBytes(StandardCharsets.UTF_8)));

		assertTrue(text.contains("    void run() {\n\t\treturn;"));
		assertEquals(StudyMaterialSourceType.FILE_CODE, storage().sourceTypeFor("A.java"));
		assertEquals(StudyMaterialSourceType.FILE_CODE, storage().sourceTypeFor("query.SQL"));
	}

	@Test
	void rejectsUnsupportedExtensions() {
		ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> storage().readAndStore(
				new MockMultipartFile("file", "virus.exe", "application/octet-stream", new byte[] {1, 2, 3})));
		assertEquals(415, error.getStatusCode().value());
	}

	@Test
	void blocksInternalAndNonHttpUrls() {
		for (String url : new String[] {
				"http://localhost/admin", "http://127.0.0.1/", "http://10.0.0.5/", "http://192.168.1.1/",
				"http://169.254.169.254/latest/meta-data", "ftp://example.com/file", "file:///etc/passwd",
				"http://example.com:8080/", "http://user:pass@example.com/" }) {
			assertThrows(ResponseStatusException.class, () -> HttpWebPageExtractor.validate(url), url);
		}
	}

	@Test
	void classifiesInternalAddresses() throws Exception {
		assertTrue(HttpWebPageExtractor.isInternal(InetAddress.getByName("100.64.1.1")));
		assertTrue(HttpWebPageExtractor.isInternal(InetAddress.getByName("fd00::1")));
		assertTrue(HttpWebPageExtractor.isInternal(InetAddress.getByName("::1")));
		assertFalse(HttpWebPageExtractor.isInternal(InetAddress.getByName("8.8.8.8")));
	}

	@Test
	void extractsMainArticleTextFromHtml() {
		String html = """
				<html><head><title>Guia de Polimorfismo</title><script>alert('x')</script></head>
				<body>
				  <nav><a href="/">Início</a> <a href="/blog">Blog</a></nav>
				  <article>
				    <h1>Polimorfismo em Java</h1>
				    <p>Polimorfismo permite que objetos de subclasses sejam tratados como objetos da superclasse.</p>
				    <ul><li>Sobrescrita de métodos</li><li>Interfaces</li></ul>
				    <pre>interface Forma {
				    double area();
				}</pre>
				  </article>
				  <footer>Copyright 2026</footer>
				</body></html>""";

		WebPageExtractor.ExtractedWebPage page = HttpWebPageExtractor.parseHtml(html, "https://example.com/guia");

		assertEquals("Guia de Polimorfismo", page.title());
		assertTrue(page.text().contains("Polimorfismo em Java"));
		assertTrue(page.text().contains("• Sobrescrita de métodos"));
		assertTrue(page.text().contains("    double area();"), "blocos <pre> preservam a indentação");
		assertFalse(page.text().contains("alert"));
		assertFalse(page.text().contains("Copyright"));
		assertFalse(page.text().contains("Blog"));
	}

	@Test
	void extractiveSummaryPicksRepresentativeSentencesInOrder() {
		String text = """
				Polimorfismo permite que objetos de subclasses sejam tratados como objetos da superclasse em Java.
				O clima estava agradável no dia da aula de programação orientada a objetos.
				Com polimorfismo, métodos sobrescritos nas subclasses são escolhidos em tempo de execução.
				Interfaces também habilitam polimorfismo, pois várias classes implementam o mesmo contrato.
				""";

		MaterialSummarizer.Summary summary = MaterialSummarizer.extractive(text);

		assertEquals("Extrativo (sem IA)", summary.source());
		assertTrue(summary.text().startsWith("Pontos-chave:"));
		assertTrue(summary.text().contains("Termos importantes:"));
		assertTrue(summary.text().contains("polimorfismo"));
		assertTrue(summary.text().indexOf("tratados como objetos") < summary.text().indexOf("tempo de execução"),
				"frases escolhidas mantêm a ordem original");
	}
}
