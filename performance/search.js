import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const schemaErrors = new Counter('schema_errors');
const cursorDuplicates = new Counter('cursor_duplicates');
const vus = Number(__ENV.VUS || 10);
const duration = __ENV.DURATION || '3m';
const baseUrl = __ENV.BASE_URL || 'http://localhost:18080';

export const options = {
  vus,
  duration,
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  thresholds: {
    http_req_failed: ['rate==0'],
    schema_errors: ['count==0'],
    cursor_duplicates: ['count==0'],
  },
};

function request(path) {
  const response = http.get(`${baseUrl}${path}`);
  const valid = check(response, {
    'status is 200': (value) => value.status === 200,
    'response has items': (value) => {
      try { return Array.isArray(value.json('items')); } catch (_) { return false; }
    },
  });
  if (!valid) schemaErrors.add(1);
  return response;
}

export default function () {
  const bucket = (__VU + __ITER) % 100;
  if (bucket < 35) {
    request('/api/v1/search?keyword=%EB%B0%98%EB%A0%A4%20%EA%B1%B4%EA%B0%95&sort=RELEVANCE');
  } else if (bucket < 60) {
    request('/api/v1/search?category=%EC%B9%B4%ED%85%8C%EA%B3%A0%EB%A6%AC3&brand=%EB%B8%8C%EB%9E%9C%EB%93%9C3&minimumPrice=10000&maximumPrice=100000&minimumRating=3&inStock=true');
  } else if (bucket < 75) {
    request('/api/v1/search?sort=POPULAR');
  } else if (bucket < 90) {
    request('/api/v1/search?sort=LATEST');
  } else {
    const first = request('/api/v1/search?sort=PRICE_ASC');
    const cursor = first.json('nextCursor');
    if (cursor) {
      const firstIds = new Set(first.json('items').map((item) => item.productId));
      const second = request(`/api/v1/search?sort=PRICE_ASC&cursor=${encodeURIComponent(cursor)}`);
      const duplicated = second.json('items').some((item) => firstIds.has(item.productId));
      if (duplicated) cursorDuplicates.add(1);
    }
  }
}

export function handleSummary(data) {
  const path = __ENV.RESULT_PATH || `performance/results/k6-${vus}.json`;
  return { [path]: JSON.stringify(data, null, 2) };
}
