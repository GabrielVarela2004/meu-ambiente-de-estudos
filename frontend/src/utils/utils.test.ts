import { describe, expect, it } from 'vitest'
import { parseTimestamp } from './timestamps'
import { extractYoutubeLinks, MAX_BATCH_LINKS } from './youtubeLinks'

describe('parseTimestamp', () => {
  it('converte MM:SS e H:MM:SS, com ou sem colchetes', () => {
    expect(parseTimestamp('03:15')).toBe(195)
    expect(parseTimestamp('[00:31]')).toBe(31)
    expect(parseTimestamp('1:02:05')).toBe(3725)
  })

  it('rejeita formatos inválidos', () => {
    expect(parseTimestamp('3:75')).toBeNull()
    expect(parseTimestamp('1:75:00')).toBeNull()
    expect(parseTimestamp('agora')).toBeNull()
  })
})

describe('extractYoutubeLinks', () => {
  it('separa os links de vídeo, descarta repetições e ignora o resto', () => {
    const result = extractYoutubeLinks(
      'https://youtu.be/dQw4w9WgXcQ?si=abc\nhttps://www.youtube.com/watch?v=dQw4w9WgXcQ&t=30, ' +
        'https://www.youtube.com/watch?v=9bZkp7q19f0&list=PL1\nnao-e-link https://vimeo.com/123 ' +
        'https://www.youtube.com/shorts/kJQP7kiw5Fk',
    )

    expect(result.links).toEqual([
      'https://youtu.be/dQw4w9WgXcQ?si=abc',
      'https://www.youtube.com/watch?v=9bZkp7q19f0&list=PL1',
      'https://www.youtube.com/shorts/kJQP7kiw5Fk',
    ])
    expect(result.ignored).toEqual(['nao-e-link', 'https://vimeo.com/123'])
    expect(result.overLimit).toBe(0)
  })

  it('respeita o limite de links por envio', () => {
    const ids = Array.from({ length: MAX_BATCH_LINKS + 2 }, (_, i) => `vid${String(i).padStart(8, '0')}`)
    const result = extractYoutubeLinks(ids.map((id) => `https://youtu.be/${id}`).join('\n'))

    expect(result.links).toHaveLength(MAX_BATCH_LINKS)
    expect(result.overLimit).toBe(2)
  })
})
