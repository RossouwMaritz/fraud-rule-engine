// k6 load test against the compose stack. How to run it: README "Load test".
// The two scenarios run one after the other, so their numbers don't mix.
import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';

const url = `${__ENV.FRAUD_URL || 'http://app:8080'}/api/v1/transactions`;
const headers = { 'Content-Type': 'application/json', 'X-API-Key': __ENV.FRAUD_API_KEY };
// Makes IDs unique per run, so a rerun is scored again instead of returning stored replays.
const run = Date.now().toString(36);

export const options = {
    scenarios: {
        // Every request is a new customer, so no two requests wait on the same advisory lock.
        many_customers: { executor: 'constant-vus', vus: 50, duration: '30s' },
        // Every request is the same customer, so they all queue on one advisory lock.
        one_customer: { executor: 'constant-vus', vus: 50, duration: '30s', startTime: '35s' },
    },
    // The thresholds only make k6 print each scenario's numbers on their own; none should fail.
    thresholds: {
        'http_req_duration{scenario:many_customers}': ['p(99)<10000'],
        'http_req_duration{scenario:one_customer}': ['p(99)<10000'],
        'http_reqs{scenario:many_customers}': ['count>0'],
        'http_reqs{scenario:one_customer}': ['count>0'],
        'http_reqs{scenario:one_customer,status:503}': ['count>=0'],
        checks: ['rate==1'],
    },
    summaryTrendStats: ['p(50)', 'p(99)', 'max'],
};

export default function () {
    const id = `lt_${run}_${__VU}_${__ITER}`;
    const busy = exec.scenario.name === 'one_customer';
    const res = http.post(url, JSON.stringify({
        transactionId: id,
        customerId: busy ? `cus_lt_${run}_busy` : `cus_${id}`,
        cardId: busy ? `card_lt_${run}_busy` : `card_${id}`,
        amountMinor: 10000 + (__ITER % 500) * 100,
        currency: 'ZAR',
        merchantId: 'mer_loadtest',
        merchantCategory: 'GROCERIES',
        channel: 'ONLINE',
        merchantCountry: 'ZA',
        occurredAt: new Date().toISOString(),
    }), { headers });
    // One customer's requests queue on one lock, so some may wait out FRAUD_LOCK_TIMEOUT and get 503. Many never should.
    check(res, { 'status is 201, or 503 for one customer': (r) => r.status === 201 || (busy && r.status === 503) });
}
