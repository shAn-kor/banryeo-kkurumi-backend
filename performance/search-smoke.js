import http from 'k6/http'
import { check, sleep } from 'k6'

export const options = { vus: 20, duration: '60s' }
const baseUrl = __ENV.BASE_URL || 'http://localhost:8080'

export default function () {
  const response = http.get(`${baseUrl}/api/v1/search?keyword=사료&size=20`)
  check(response, { 'search returns 200': (result) => result.status === 200 })
  sleep(0.2)
}
