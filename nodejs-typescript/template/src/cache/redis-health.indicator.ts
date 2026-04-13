{{- if or (eq stack_profile "relational-db") (eq stack_profile "nosql-cache") }}
import { Inject, Injectable } from '@nestjs/common';
import {
  HealthIndicator,
  HealthIndicatorResult,
  HealthCheckError,
} from '@nestjs/terminus';
import type Redis from 'ioredis';

import { REDIS_CLIENT } from './redis.tokens';

@Injectable()
export class RedisHealthIndicator extends HealthIndicator {
  constructor(@Inject(REDIS_CLIENT) private readonly redis: Redis) {
    super();
  }

  async isHealthy(key: string): Promise<HealthIndicatorResult> {
    try {
      const pong = await this.redis.ping();
      if (pong !== 'PONG') throw new Error(`unexpected ping response: ${pong}`);
      return this.getStatus(key, true);
    } catch (err) {
      throw new HealthCheckError(
        'Redis ping failed',
        this.getStatus(key, false, { error: (err as Error).message }),
      );
    }
  }
}
{{- end }}
