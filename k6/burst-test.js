import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const BURST_SIZE = 20;

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: {
    checks: ['rate==1.0'],
  },
};

export default function () {
  const payload = JSON.stringify({
    product: 'Widget',
    quantity: 1,
    customerEmail: 'buyer@example.com',
  });
  const params = {
    headers: { 'Content-Type': 'application/json' },
  };

  const requests = [];
  for (let i = 0; i < BURST_SIZE; i++) {
    requests.push(['POST', `${BASE_URL}/orders`, payload, params]);
  }

  const responses = http.batch(requests);

  const allCreated = responses.every((res) => res.status === 201);
  const outcomes = responses.map((res) => {
    try {
      return res.json('notificationOutcome');
    } catch (e) {
      return undefined;
    }
  });
  const sawSent = outcomes.includes('SENT');
  const sawRateLimited = outcomes.includes('RATE_LIMITED');

  check(null, {
    'every order was created (201)': () => allCreated,
    'at least one notification was SENT': () => sawSent,
    'at least one notification was RATE_LIMITED': () => sawRateLimited,
  });

  console.log(`Outcomes: ${JSON.stringify(outcomes)}`);
}
