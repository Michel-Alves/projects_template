import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { LoggerModule } from 'nestjs-pino';
import { PrometheusModule } from '@willsoto/nestjs-prometheus';
import { TerminusModule } from '@nestjs/terminus';

import configuration from './config/configuration';
import { HealthModule } from './health/health.module';
import { MessagingModule } from './messaging/messaging.module';
import { BlobsModule } from './blobs/blobs.module';
{{if or (eq stack_profile "relational-db") (eq stack_profile "nosql-cache")}}import { CacheModule } from './cache/cache.module';
{{end}}{{if eq stack_profile "relational-db"}}import { SampleEntityModule } from './sample/sample-entity.module';
{{end}}{{if eq stack_profile "nosql-cache"}}import { MongoModule } from './mongo/mongo.module';
import { SampleDocumentModule } from './sample/sample-document.module';
{{end}}

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      load: [configuration],
    }),
    LoggerModule.forRoot({
      pinoHttp: {
        level: process.env.LOG_LEVEL ?? 'info',
        formatters: {
          level: (label) => ({ level: label }),
        },
        messageKey: 'message',
        timestamp: () => `,"timestamp":"${new Date().toISOString()}"`,
      },
    }),
    PrometheusModule.register({
      defaultMetrics: { enabled: true },
    }),
    TerminusModule,
    HealthModule,
    MessagingModule,
    BlobsModule,{{if or (eq stack_profile "relational-db") (eq stack_profile "nosql-cache")}}
    CacheModule,{{end}}{{if eq stack_profile "relational-db"}}
    SampleEntityModule,{{end}}{{if eq stack_profile "nosql-cache"}}
    MongoModule,
    SampleDocumentModule,{{end}}
  ],
})
export class AppModule {}
