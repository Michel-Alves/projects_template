{{- if or (eq stack_profile "relational-db") (eq stack_profile "nosql-cache") }}
import { Inject, Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import type Redis from 'ioredis';

import type { AppConfig } from '../config/configuration';
import { REDIS_CLIENT } from './redis.tokens';

@Injectable()
export class SampleCache {
  private readonly prefix = 'sample:';
  private readonly ttlSeconds: number;

  constructor(
    @Inject(REDIS_CLIENT) private readonly redis: Redis,
    config: ConfigService<AppConfig, true>,
  ) {
    this.ttlSeconds = config.get('redis', { infer: true }).cacheTtlSeconds;
  }

  async get(key: string): Promise<string | null> {
    return this.redis.get(this.prefix + key);
  }

  async put(key: string, value: string): Promise<void> {
    await this.redis.set(this.prefix + key, value, 'EX', this.ttlSeconds);
  }

  async evict(key: string): Promise<void> {
    await this.redis.del(this.prefix + key);
  }
}
{{- end }}
