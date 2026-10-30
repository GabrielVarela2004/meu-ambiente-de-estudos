package br.com.estudos.estudos_backend.question;

/**
 * O que pode ser exibido de uma questão antes da resposta. Em questões abertas, a resposta-modelo, a resposta
 * esperada, a explicação e a rubrica só aparecem no feedback, depois de responder.
 */
final class StudyQuestionVisibility {

	private StudyQuestionVisibility() {
	}

	static String expectedAnswerBeforeAnswer(StudyQuestion question) {
		return question.getFormat() == StudyQuestionFormat.DISCURSIVE ? null : question.getExpectedAnswer();
	}

	static String explanationBeforeAnswer(StudyQuestion question) {
		return question.getFormat() == StudyQuestionFormat.DISCURSIVE ? null : question.getExplanation();
	}
}
