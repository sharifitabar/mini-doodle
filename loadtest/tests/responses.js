import exec from 'k6/execution';
import { manipulate, options as bookingOptions, handleSummary } from '../k6/booking.js';

const users = __ENV.RESPONSE_USERS.split(',');
const thresholds = { ...bookingOptions.thresholds };

// Fixture timings and the request mix are not performance measurements.
delete thresholds['http_req_duration{flow:availability}'];
delete thresholds.manipulation_sequence_duration;
delete thresholds.manipulation_share;

export const options = {
  scenarios: {
    responses: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: users.length,
      maxDuration: '30s',
    },
  },
  thresholds,
};

export default function () {
  const iteration = exec.scenario.iterationInTest;
  manipulate(users[iteration], 1, iteration);
}

export { handleSummary };
