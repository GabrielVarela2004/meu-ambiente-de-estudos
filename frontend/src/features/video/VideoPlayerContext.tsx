import { useCallback, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { YouTubePlayer } from '../../components/video/YouTubePlayer'
import type { YouTubePlayerHandle } from '../../components/video/YouTubePlayer'
import '../../styles/pages/video.css'
import { VideoPlayerContext } from './videoPlayer'
import type { VideoToOpen } from './videoPlayer'

/** Player flutuante compartilhado (chat e outras telas abrem vídeos em um minuto específico). */
export function VideoPlayerProvider({ children }: { children: ReactNode }) {
  const [video, setVideo] = useState<VideoToOpen | null>(null)
  const currentRef = useRef<VideoToOpen | null>(null)
  const playerRef = useRef<YouTubePlayerHandle>(null)

  const openVideo = useCallback((next: VideoToOpen) => {
    if (currentRef.current?.videoId === next.videoId) {
      playerRef.current?.seekTo(next.seconds ?? 0)
      return
    }
    currentRef.current = next
    setVideo(next)
  }, [])

  const close = useCallback(() => {
    currentRef.current = null
    setVideo(null)
  }, [])

  const value = useMemo(() => ({ openVideo }), [openVideo])

  return (
    <VideoPlayerContext.Provider value={value}>
      {children}
      {video && (
        <aside className="video-floating-player card" aria-label={`Player do vídeo ${video.title}`}>
          <div className="video-floating-header">
            <strong className="video-floating-title">{video.title}</strong>
            <button type="button" className="btn btn-ghost btn-xs" onClick={close} aria-label="Fechar o vídeo">
              ✕
            </button>
          </div>
          <YouTubePlayer
            key={video.videoId}
            ref={playerRef}
            videoId={video.videoId}
            title={video.title}
            startSeconds={video.seconds ?? 0}
          />
        </aside>
      )}
    </VideoPlayerContext.Provider>
  )
}
