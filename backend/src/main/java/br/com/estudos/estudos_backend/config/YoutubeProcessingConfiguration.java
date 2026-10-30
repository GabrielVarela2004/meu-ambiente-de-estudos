package br.com.estudos.estudos_backend.config;

import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Executor do processamento de vídeos do YouTube (análise por IA ou transcrição), fora da requisição.
 * Com {@code app.youtube.processing.async=false} (perfil de teste) roda na própria thread, de forma síncrona.
 */
@Configuration
public class YoutubeProcessingConfiguration {

	public static final String EXECUTOR = "youtubeProcessingExecutor";

	@Bean(EXECUTOR)
	public Executor youtubeProcessingExecutor(@Value("${app.youtube.processing.async:true}") boolean async) {
		if (!async) {
			return new SyncTaskExecutor();
		}
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		// Poucas threads: cada análise é uma chamada longa e o plano gratuito tem limite por minuto.
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(2);
		executor.setQueueCapacity(50);
		executor.setThreadNamePrefix("youtube-");
		executor.setWaitForTasksToCompleteOnShutdown(false);
		executor.initialize();
		return executor;
	}
}
