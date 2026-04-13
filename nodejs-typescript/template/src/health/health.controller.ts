import { Controller, Get } from '@nestjs/common';
import {
  HealthCheck,
  HealthCheckService,
  HealthIndicatorResult,
} from '@nestjs/terminus';
{{if or (eq stack_profile "relational-db") (eq stack_profile "nosql-cache")}}import { RedisHealthIndicator } from '../cache/redis-health.indicator';
{{end}}{{if eq stack_profile "nosql-cache"}}import { MongoHealthIndicator } from './mongo.indicator';
{{end}}{{if eq stack_profile "relational-db"}}import { PostgresHealthIndicator } from './postgres.indicator';
{{end}}

@Controller('health')
export class HealthController {
  constructor(
    private readonly health: HealthCheckService,{{if or (eq stack_profile "relational-db") (eq stack_profile "nosql-cache")}}
    private readonly redis: RedisHealthIndicator,{{end}}{{if eq stack_profile "nosql-cache"}}
    private readonly mongo: MongoHealthIndicator,{{end}}{{if eq stack_profile "relational-db"}}
    private readonly postgres: PostgresHealthIndicator,{{end}}
  ) {}

  @Get()
  @HealthCheck()
  check() {
    return this.health.check([
      async (): Promise<HealthIndicatorResult> => ({ app: { status: 'up' } }),
    ]);
  }

  @Get('liveness')
  @HealthCheck()
  liveness() {
    return this.health.check([
      async (): Promise<HealthIndicatorResult> => ({ app: { status: 'up' } }),
    ]);
  }

  @Get('readiness')
  @HealthCheck()
  readiness() {
    return this.health.check([{{if eq stack_profile "relational-db"}}
      () => this.postgres.isHealthy('postgres'),{{end}}{{if eq stack_profile "nosql-cache"}}
      () => this.mongo.isHealthy('mongo'),{{end}}{{if or (eq stack_profile "relational-db") (eq stack_profile "nosql-cache")}}
      () => this.redis.isHealthy('redis'),{{end}}
    ]);
  }
}
