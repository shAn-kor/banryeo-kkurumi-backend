import { defineConfig } from 'vitepress'

export default defineConfig({
  lang: 'ko-KR',
  title: '반려꾸러미 백엔드',
  description: 'Spring Modulith 커머스 백엔드',
  base: '/banryeo-kkurumi-backend/',
  themeConfig: {
    nav: [
      { text: '모듈', link: '/modules' },
      { text: '주문 흐름', link: '/order-convergence' },
      { text: 'API', link: '/api' },
      { text: '검증', link: '/verification' }
    ],
    sidebar: [
      { text: '소개', link: '/' },
      { text: '모듈 지도', link: '/modules' },
      { text: '주문 상태수렴', link: '/order-convergence' },
      { text: 'API 표면', link: '/api' },
      { text: '검증 기준', link: '/verification' }
    ]
  }
})
