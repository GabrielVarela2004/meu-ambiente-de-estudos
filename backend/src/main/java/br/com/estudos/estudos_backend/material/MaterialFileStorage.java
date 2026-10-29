package br.com.estudos.estudos_backend.material;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
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
			throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
					"Only .txt, .md, .pdf, and .docx files are supported");
		}
		long maxBytes = (ext.equals(".pdf") || ext.equals(".docx")) ? 5_000_000L : 1_000_000L;
		if (file.getSize() > maxBytes) {
			throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE,
					"File size exceeds the maximum allowed limit");
		}
		try {
			Files.createDirectories(storageDirectory);
			byte[] bytes = file.getBytes();
			String extracted = extractText(bytes, ext).trim();
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

	private String extractText(byte[] bytes, String ext) {
		if (ext.equals(".txt") || ext.equals(".md")) {
			String content = new String(bytes, StandardCharsets.UTF_8);
			return content.startsWith("\uFEFF") ? content.substring(1) : content;
		}
		if (ext.equals(".pdf")) {
			return extractPdfText(bytes);
		}
		if (ext.equals(".docx")) {
			return extractDocxText(bytes);
		}
		throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported file extension");
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
					ByteArrayOutputStream out = new ByteArrayOutputStream();
					byte[] buffer = new byte[4096];
					int read;
					int total = 0;
					while ((read = zis.read(buffer)) != -1) {
						total += read;
						if (total > MAX_DOCX_XML_BYTES) {
							throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
									"DOCX internal XML exceeds maximum safe size");
						}
						out.write(buffer, 0, read);
					}
					documentXml = out.toByteArray();
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
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document doc = builder.parse(new ByteArrayInputStream(documentXml));
			StringBuilder sb = new StringBuilder();
			NodeList paragraphs = doc.getElementsByTagName("w:p");
			if (paragraphs.getLength() == 0) {
				paragraphs = doc.getElementsByTagName("p");
			}
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
		} catch (Exception exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid XML structure in DOCX file");
		}
	}

	private String extension(String filename) {
		if (filename.endsWith(".md")) return ".md";
		if (filename.endsWith(".txt")) return ".txt";
		if (filename.endsWith(".pdf")) return ".pdf";
		if (filename.endsWith(".docx")) return ".docx";
		return null;
	}
}
