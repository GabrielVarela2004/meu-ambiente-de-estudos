import { forwardRef, useEffect, useImperativeHandle, useRef } from 'react'

// Tipagem mínima da YouTube IFrame Player API (https://developers.google.com/youtube/iframe_api_reference).
type YTPlayer = {
  seekTo: (seconds: number, allowSeekAhead: boolean) => void
  playVideo: () => void
  destroy: () => void
  getIframe: () => HTMLIFrameElement
}

type YTNamespace = {
  Player: new (element: HTMLElement, options: Record<string, unknown>) => YTPlayer
}

declare global {
  interface Window {
    YT?: YTNamespace
    onYouTubeIframeAPIReady?: () => void
  }
}

let apiPromise: Promise<void> | null = null

/** Carrega o script da IFrame API uma única vez por página. */
function loadYouTubeApi(): Promise<void> {
  if (window.YT?.Player) return Promise.resolve()
  if (!apiPromise) {
    apiPromise = new Promise((resolve) => {
      const previous = window.onYouTubeIframeAPIReady
      window.onYouTubeIframeAPIReady = () => {
        previous?.()
        resolve()
      }
      const script = document.createElement('script')
      script.src = 'https://www.youtube.com/iframe_api'
      script.async = true
      document.head.appendChild(script)
    })
  }
  return apiPromise
}

export type YouTubePlayerHandle = {
  /** Pula para o segundo indicado e começa a tocar (se o player ainda carrega, pula assim que ficar pronto). */
  seekTo: (seconds: number) => void
}

type Props = {
  videoId: string
  title: string
  startSeconds?: number
}

/** Player incorporado (youtube-nocookie.com) controlado pela IFrame Player API. */
export const YouTubePlayer = forwardRef<YouTubePlayerHandle, Props>(function YouTubePlayer(
  { videoId, title, startSeconds = 0 },
  ref,
) {
  const containerRef = useRef<HTMLDivElement>(null)
  const playerRef = useRef<YTPlayer | null>(null)
  const readyRef = useRef(false)
  const pendingSeekRef = useRef<number | null>(null)
  // Só o valor inicial importa: depois, a navegação é por seekTo.
  const initialStartRef = useRef(startSeconds)
  const titleRef = useRef(title)

  useEffect(() => {
    titleRef.current = title
  }, [title])

  useImperativeHandle(ref, () => ({
    seekTo(seconds: number) {
      const player = playerRef.current
      if (player && readyRef.current) {
        player.seekTo(seconds, true)
        player.playVideo()
      } else {
        pendingSeekRef.current = seconds
      }
    },
  }), [])

  useEffect(() => {
    let cancelled = false
    const mountPoint = document.createElement('div')
    containerRef.current?.appendChild(mountPoint)

    void loadYouTubeApi().then(() => {
      if (cancelled || !window.YT) return
      playerRef.current = new window.YT.Player(mountPoint, {
        host: 'https://www.youtube-nocookie.com',
        videoId,
        width: '100%',
        height: '100%',
        playerVars: { start: Math.floor(initialStartRef.current), rel: 0, playsinline: 1 },
        events: {
          onReady: () => {
            readyRef.current = true
            const player = playerRef.current
            if (!player) return
            player.getIframe().title = `Vídeo: ${titleRef.current}`
            if (pendingSeekRef.current !== null) {
              player.seekTo(pendingSeekRef.current, true)
              player.playVideo()
              pendingSeekRef.current = null
            }
          },
        },
      })
    })

    return () => {
      cancelled = true
      readyRef.current = false
      playerRef.current?.destroy()
      playerRef.current = null
      mountPoint.remove()
    }
  }, [videoId])

  return (
    <div className="youtube-player">
      <div ref={containerRef} className="youtube-player-frame" />
    </div>
  )
})
