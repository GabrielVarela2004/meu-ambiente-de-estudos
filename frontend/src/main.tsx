import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import './styles/components.css'
import App from './App.tsx'
import { VideoPlayerProvider } from './features/video/VideoPlayerContext'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <VideoPlayerProvider>
      <App />
    </VideoPlayerProvider>
  </StrictMode>,
)
