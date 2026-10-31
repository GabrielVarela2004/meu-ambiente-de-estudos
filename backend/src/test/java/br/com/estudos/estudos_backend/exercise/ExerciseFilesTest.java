package br.com.estudos.estudos_backend.exercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import br.com.estudos.estudos_backend.common.exception.InvalidInputException;

class ExerciseFilesTest {

	@ParameterizedTest
	@ValueSource(strings = { "../Main.java", "loja/../../Main.java", "/etc/Main.java", "C:/Main.java",
			"..\\Main.java", "Main.txt", "Main", "loja/Main.java/", "1Main.java", "lo ja/Main.java", "" })
	void blocksPathTraversalAbsolutePathsAndNonJavaFiles(String path) {
		assertThatThrownBy(() -> ExerciseFiles.validate(List.of(new ExerciseFile(path, "class A {}"))))
				.isInstanceOf(InvalidInputException.class);
	}

	@Test
	void acceptsPackagesAndNormalizesBackslashes() {
		List<ExerciseFile> files = ExerciseFiles.validate(List.of(
				new ExerciseFile("Main.java", "public class Main {}"),
				new ExerciseFile("loja\\modelo\\Pedido.java", "package loja.modelo; public record Pedido() {}")));

		assertThat(files).extracting(ExerciseFile::path).containsExactly("Main.java", "loja/modelo/Pedido.java");
	}

	@Test
	void rejectsDuplicatesIgnoringCase() {
		assertThatThrownBy(() -> ExerciseFiles.validate(List.of(
				new ExerciseFile("Main.java", ""), new ExerciseFile("main.java", ""))))
				.hasMessageContaining("duplicado");
	}

	@Test
	void enforcesFileCountAndTotalSizeLimits() {
		List<ExerciseFile> many = new ArrayList<>();
		for (int i = 0; i <= ExerciseFiles.MAX_FILES; i++) {
			many.add(new ExerciseFile("A" + i + ".java", "class A" + i + " {}"));
		}
		assertThatThrownBy(() -> ExerciseFiles.validate(many)).hasMessageContaining("no máximo 20");

		String big = "x".repeat(60 * 1024);
		assertThatThrownBy(() -> ExerciseFiles.validate(List.of(new ExerciseFile("A.java", big), new ExerciseFile("B.java", big))))
				.hasMessageContaining("100 KB");

		assertThatThrownBy(() -> ExerciseFiles.validate(List.of())).isInstanceOf(InvalidInputException.class);
	}

	@Test
	void oldSingleTextSubmissionBecomesOneFileNamedAfterThePublicType() {
		assertThat(ExerciseFiles.fromSingleSource("class Aux {}\npublic final class Calculadora {}"))
				.singleElement().satisfies(f -> assertThat(f.path()).isEqualTo("Calculadora.java"));
		assertThat(ExerciseFiles.fromSingleSource("enum Cor { AZUL }")).singleElement()
				.satisfies(f -> assertThat(f.path()).isEqualTo("Cor.java"));
		assertThat(ExerciseFiles.fromSingleSource("System.out.println(1);")).singleElement()
				.satisfies(f -> assertThat(f.path()).isEqualTo("Main.java"));
	}

	@Test
	void jsonRoundTripAndJoinedText() {
		List<ExerciseFile> files = List.of(new ExerciseFile("Main.java", "class Main {}"),
				new ExerciseFile("loja/Status.java", "package loja; enum Status { A }"));

		assertThat(ExerciseFiles.fromJson(ExerciseFiles.toJson(files))).isEqualTo(files);
		assertThat(ExerciseFiles.fromJson("lixo")).isEmpty();
		assertThat(ExerciseFiles.joined(files)).contains("// Arquivo: Main.java").contains("// Arquivo: loja/Status.java");
		assertThat(ExerciseFiles.joined(List.of(files.get(0)))).isEqualTo("class Main {}");
	}
}
