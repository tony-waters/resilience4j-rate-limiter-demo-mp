import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
// email-service allows 5 requests per 10s. Wave 1 must exceed that to force a live
// 429 (teaching rest-service the budget is gone); wave 2 fires immediately after,
// still inside the 10s window, so rest-service should skip those calls entirely
// instead of making them. See ADR-0004.
const WAVE_ONE_SIZE = 10;
const WAVE_TWO_SIZE = 5;

export const options = {
  vus: 5,
  iterations: 5,
  thresholds: {
    checks: ['rate==1.0'],
  },
};

function placeOrders(count) {
  const payload = JSON.stringify({
    product: 'Widget',
    quantity: 1,
    customerEmail: 'buyer@example.com',
  });
  const params = {
    headers: { 'Content-Type': 'application/json' },
  };

  const requests = [];
  for (let i = 0; i < count; i++) {
    requests.push(['POST', `${BASE_URL}/orders`, payload, params]);
  }

  return http.batch(requests).map((res) => {
    try {
      return { status: res.status, outcome: res.json('notificationOutcome') };
    } catch (e) {
      return { status: res.status, outcome: undefined };
    }
  });
}

export default function () {
  const waveOne = placeOrders(WAVE_ONE_SIZE);
  const waveTwo = placeOrders(WAVE_TWO_SIZE);

  const allCreated = waveOne.concat(waveTwo).every((r) => r.status === 201);
  const waveOneOutcomes = waveOne.map((r) => r.outcome);
  const waveTwoOutcomes = waveTwo.map((r) => r.outcome);

  check(null, {
    'every order was created (201)': () => allCreated,
    'wave 1 saw a SENT notification': () => waveOneOutcomes.includes('SENT'),
    'wave 1 saw a RATE_LIMITED notification': () => waveOneOutcomes.includes('RATE_LIMITED'),
    'wave 2 saw a SKIPPED notification': () => waveTwoOutcomes.includes('SKIPPED'),
  });

  console.log(`Wave 1 outcomes: ${JSON.stringify(waveOneOutcomes)}`);
  console.log(`Wave 2 outcomes: ${JSON.stringify(waveTwoOutcomes)}`);
}
