import { Module } from '@nestjs/common';
import { TerminusModule } from '@nestjs/terminus';

import { HealthController } from './health.controller';
{{if or (eq stack_profile "relational-db") (eq stack_profile "nosql-cache")}}import { CacheModule } from '../cache/cache.module';
{{end}}{{if eq stack_profile "nosql-cache"}}import { MongoModule } from '../mongo/mongo.module';
import { MongoHealthIndicator } from './mongo.indicator';
{{end}}{{if eq stack_profile "relational-db"}}import { PostgresHealthIndicator } from './postgres.indicator';
import { SampleEntityModule } from '../sample/sample-entity.module';
{{end}}

@Module({
  imports: [
    TerminusModule,{{if or (eq stack_profile "relational-db") (eq stack_profile "nosql-cache")}}
    CacheModule,{{end}}{{if eq stack_profile "nosql-cache"}}
    MongoModule,{{end}}{{if eq stack_profile "relational-db"}}
    SampleEntityModule,{{end}}
  ],
  controllers: [HealthController],
  providers: [{{if eq stack_profile "nosql-cache"}}MongoHealthIndicator{{end}}{{if eq stack_profile "relational-db"}}PostgresHealthIndicator{{end}}],
})
export class HealthModule {}
