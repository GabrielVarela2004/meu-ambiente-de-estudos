package br.com.estudos.estudos_backend.career.domain;

/** Andamento da candidatura do usuário em uma vaga encontrada pelo agente. */
public enum JobApplicationStatus {
	DISCOVERED,
	APPLIED,
	INTERVIEWING,
	OFFER,
	REJECTED,
	DISCARDED
}
