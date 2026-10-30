import { createContext, useContext } from 'react'

export type VideoToOpen = {
  videoId: string
  title: string
  seconds?: number
}

export type VideoPlayerContextValue = {
  /** Abre o player flutuante no vídeo indicado; se ele já está aberto, só pula para o segundo pedido. */
  openVideo: (video: VideoToOpen) => void
}

export const VideoPlayerContext = createContext<VideoPlayerContextValue | null>(null)

/** Pode ser null fora do provider (ex.: testes de componentes isolados). */
export function useVideoPlayer(): VideoPlayerContextValue | null {
  return useContext(VideoPlayerContext)
}
