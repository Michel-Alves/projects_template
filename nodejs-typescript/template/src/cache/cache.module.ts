{{- if or (eq stack_profile "relational-db") (eq stack_profile "nosql-cache") }}
import { Global, Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import Redis from 'ioredis';

import type { AppConfig } from '../config/configuration';
import { SampleCache } from './sample-cache.service';
import { RedisHealthIndicator } from './redis-health.indicator';
import { REDIS_CLIENT } from './redis.tokens';

@Global()
@Module({
  providers: [
    {
      provide: REDIS_CLIENT,
      inject: [ConfigService],
      useFactory: (config: ConfigService<AppConfig, true>) => {
        const redis = config.get('redis', { infer: true });
        return new Redis({ host: redis.host, port: redis.port, lazyConnect: false });
      },
    },
    SampleCache,
    RedisHealthIndicator,
  ],
  exports: [REDIS_CLIENT, SampleCache, RedisHealthIndicator],
})
export class CacheModule {}
{{- end }}
