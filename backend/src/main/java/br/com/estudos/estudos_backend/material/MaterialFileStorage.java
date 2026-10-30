package br.com.estudos.estudos_backend.material;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@Service
public class MaterialFileStorage {
	private static final int MAX_EXTRACTED_CHARS = 100_000;
	private static final int MAX_DOCX_XML_BYTES = 5_000_000;
	/** Limites do PPTX contra zip-bomb: por parte XML, total descompactado e número de entradas. */
	private static final int MAX_PPTX_PART_BYTES = 5_000_000;
	private static final long MAX_PPTX_TOTAL_BYTES = 50_000_000L;
	private static final int MAX_PPTX_ENTRIES = 5_000;
	private static final long MAX_BINARY_FILE_BYTES = 20_000_000L;
	private static final long MAX_TEXT_FILE_BYTES = 2_000_000L;

	private static final Pattern SLIDE_ENTRY = Pattern.compile("ppt/slides/slide(\\d+)\\.xml");
	private static final Pattern NOTES_ENTRY = Pattern.compile("ppt/notesSlides/notesSlide(\\d+)\\.xml");

	/** Extensões de código-fonte e configuração tratadas como texto puro. */
	static final Set<String> CODE_EXTENSIONS = Set.of(
			".java", ".kt", ".py", ".js", ".jsx", ".ts", ".tsx", ".c", ".h", ".cpp", ".hpp", ".cs", ".go",
			".rb", ".php", ".rs", ".swift", ".scala", ".sql", ".sh", ".ps1", ".html", ".css", ".scss",
			".json", ".yml", ".yaml", ".xml", ".gradle", ".properties", ".ipynb");

	public static final String SUPPORTED_MESSAGE =
			"Formatos suportados: .txt, .md, .pdf, .docx, .pptx e arquivos de código (.java, .py, .ts, .sql…)";

	private final Path storageDirectory;

	public MaterialFileStorage(@Value("${STUDY_FILE_STORAGE_DIR:${user.home}/.estudos/uploads}") String storageDirectory) {
		this.storageDirectory = Paths.get(storageDirectory).toAbsolutePath().normalize();
	}

	public String readAndStore(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A non-empty study file is required");
		}
		String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
		String lowerName = originalName.toLowerCase();
		String ext = extension(lowerName);
		if (ext == null) {
			throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, SUPPORTED_MESSAGE);
		}
		long maxBytes = isBinary(ext) ? MAX_BINARY_FILE_BYTES : MAX_TEXT_FILE_BYTES;
		if (file.getSize() > maxBytes) {
			throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE,
					"File size exceeds the maximum allowed limit");
		}
		try {
			Files.createDirectories(storageDirectory);
			byte[] bytes = file.getBytes();
			String rawExtracted = extractText(bytes, ext);
			String extracted = CODE_EXTENSIONS.contains(ext)
					? normalizeCode(rawExtracted)
					: sanitizeExtractedText(rawExtracted);
			if (extracted.isBlank()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"The uploaded file does not contain extractable text");
			}
			String storedName = UUID.randomUUID() + ext;
			Files.write(storageDirectory.resolve(storedName), bytes);
			return extracted.length() > MAX_EXTRACTED_CHARS
					? extracted.substring(0, MAX_EXTRACTED_CHARS)
					: extracted;
		} catch (ResponseStatusException exception) {
			throw exception;
		} catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
					"Could not store or process the study file");
		}
	}

	/** Tipo de fonte correspondente ao nome do arquivo (usado ao salvar o material). */
	public StudyMaterialSourceType sourceTypeFor(String filename) {
		String ext = extension(filename == null ? "" : filename.toLowerCase());
		if (ext == null) {
			return StudyMaterialSourceType.FILE_TXT;
		}
		return switch (ext) {
			case ".pdf" -> StudyMaterialSourceType.FILE_PDF;
			case ".docx" -> StudyMaterialSourceType.FILE_DOCX;
			case ".pptx" -> StudyMaterialSourceType.FILE_PPTX;
			case ".md" -> StudyMaterialSourceType.FILE_MD;
			case ".txt" -> StudyMaterialSourceType.FILE_TXT;
			default -> StudyMaterialSourceType.FILE_CODE;
		};
	}

	/**
	 * Higieniza o texto extraído para eliminar caracteres de controle, normalizar quebras
	 * de linha e evitar desperdício de tokens na OpenAI preservando parágrafos legíveis.
	 */
	public String sanitizeExtractedText(String text) {
		if (text == null) {
			return "";
		}
		String cleaned = text.replace("\u0000", "")
				.replace("\u000B", "")
				.replace("\f", "\n")
				.replace("\r\n", "\n")
				.replace("\r", "\n");

		cleaned = cleaned.replaceAll("[ \\t\\x0B]+", " ");
		cleaned = cleaned.replaceAll(" *\\n *", "\n");
		cleaned = cleaned.replaceAll("\\n{3,}", "\n\n");
		return cleaned.trim();
	}

	private String extractText(byte[] bytes, String ext) {
		if (ext.equals(".pdf")) {
			return extractPdfText(bytes);
		}
		if (ext.equals(".docx")) {
			return extractDocxText(bytes);
		}
		if (ext.equals(".pptx")) {
			return extractPptxText(bytes);
		}
		return readUtf8(bytes);
	}

	/** Código preserva a indentação: só remove caracteres nulos e normaliza quebras de linha. */
	static String normalizeCode(String text) {
		return text.replace("\u0000", "")
				.replace("\r\n", "\n")
				.replace("\r", "\n")
				.replaceAll("\\n{4,}", "\n\n\n")
				.strip();
	}

	private static String readUtf8(byte[] bytes) {
		String content = new String(bytes, StandardCharsets.UTF_8);
		return content.startsWith("﻿") ? content.substring(1) : content;
	}

	private String extractPdfText(byte[] bytes) {
		try (PDDocument document = Loader.loadPDF(bytes)) {
			if (document.isEncrypted()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Encrypted PDF files are not supported");
			}
			PDFTextStripper stripper = new PDFTextStripper();
			return stripper.getText(document);
		} catch (ResponseStatusException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or corrupted PDF file");
		}
	}

	private String extractDocxText(byte[] bytes) {
		byte[] documentXml = null;
		try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				if ("word/document.xml".equals(entry.getName())) {
					documentXml = readLimited(zis, MAX_DOCX_XML_BYTES, "DOCX internal XML exceeds maximum safe size");
					break;
				}
			}
		} catch (ResponseStatusException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or corrupted DOCX archive");
		}

		if (documentXml == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid DOCX file: missing word/document.xml");
		}

		try {
			Document doc = secureBuilder().parse(new ByteArrayInputStream(documentXml));
			return paragraphsText(doc, "w:p", "p");
		} catch (Exception exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid XML structure in DOCX file");
		}
	}

	/** Texto de cada slide (em ordem) seguido das anotações do apresentador, quando existem. */
	private String extractPptxText(byte[] bytes) {
		Map<Integer, byte[]> slides = new TreeMap<>();
		Map<Integer, byte[]> notes = new TreeMap<>();
		try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
			ZipEntry entry;
			int entries = 0;
			long total = 0;
			while ((entry = zis.getNextEntry()) != null) {
				if (++entries > MAX_PPTX_ENTRIES) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PPTX archive has too many entries");
				}
				Matcher slide = SLIDE_ENTRY.matcher(entry.getName());
				Matcher note = NOTES_ENTRY.matcher(entry.getName());
				if (!slide.matches() && !note.matches()) {
					continue;
				}
				byte[] xml = readLimited(zis, MAX_PPTX_PART_BYTES, "PPTX slide XML exceeds maximum safe size");
				total += xml.length;
				if (total > MAX_PPTX_TOTAL_BYTES) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PPTX content exceeds maximum safe size");
				}
				if (slide.matches()) {
					slides.put(Integer.parseInt(slide.group(1)), xml);
				} else {
					notes.put(Integer.parseInt(note.group(1)), xml);
				}
			}
		} catch (ResponseStatusException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or corrupted PPTX archive");
		}

		if (slides.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid PPTX file: no slides found");
		}

		try {
			DocumentBuilder builder = secureBuilder();
			StringBuilder out = new StringBuilder();
			for (Map.Entry<Integer, byte[]> slide : slides.entrySet()) {
				String slideText = paragraphsText(builder.parse(new ByteArrayInputStream(slide.getValue())), "a:p", "p");
				String notesText = "";
				byte[] notesXml = notes.get(slide.getKey());
				if (notesXml != null) {
					notesText = stripSlideNumber(paragraphsText(builder.parse(new ByteArrayInputStream(notesXml)), "a:p", "p"));
				}
				if (slideText.isBlank() && notesText.isBlank()) {
					continue;
				}
				out.append("Slide ").append(slide.getKey()).append('\n');
				if (!slideText.isBlank()) {
					out.append(slideText).append('\n');
				}
				if (!notesText.isBlank()) {
					out.append("Anotações: ").append(notesText).append('\n');
				}
				out.append('\n');
			}
			return out.toString();
		} catch (Exception exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid XML structure in PPTX file");
		}
	}

	/** As anotações costumam terminar com o número do slide como parágrafo isolado. */
	private static String stripSlideNumber(String notes) {
		return notes.replaceAll("(?m)^\\d{1,4}$\\n?", "").trim();
	}

	private static byte[] readLimited(ZipInputStream zis, int maxBytes, String limitMessage) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[4096];
		int read;
		int total = 0;
		while ((read = zis.read(buffer)) != -1) {
			total += read;
			if (total > maxBytes) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, limitMessage);
			}
			out.write(buffer, 0, read);
		}
		return out.toByteArray();
	}

	private static DocumentBuilder secureBuilder() throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		factory.setXIncludeAware(false);
		factory.setExpandEntityReferences(false);
		return factory.newDocumentBuilder();
	}

	private static String paragraphsText(Document doc, String qualifiedTag, String fallbackTag) {
		NodeList paragraphs = doc.getElementsByTagName(qualifiedTag);
		if (paragraphs.getLength() == 0) {
			paragraphs = doc.getElementsByTagName(fallbackTag);
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < paragraphs.getLength(); i++) {
			Node p = paragraphs.item(i);
			String text = p.getTextContent();
			if (text != null && !text.isBlank()) {
				if (!sb.isEmpty()) {
					sb.append("\n");
				}
				sb.append(text.trim());
			}
		}
		return sb.toString();
	}

	private static boolean isBinary(String ext) {
		return ext.equals(".pdf") || ext.equals(".docx") || ext.equals(".pptx");
	}

	private String extension(String filename) {
		int dot = filename.lastIndexOf('.');
		if (dot < 0) {
			return null;
		}
		String ext = filename.substring(dot);
		if (Set.of(".md", ".txt", ".pdf", ".docx", ".pptx").contains(ext) || CODE_EXTENSIONS.contains(ext)) {
			return ext;
		}
		return null;
	}
}
