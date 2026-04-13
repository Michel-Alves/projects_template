{{- if eq stack_profile "nosql-cache" }}
import { Global, Module, OnApplicationShutdown } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { MongoClient, Db } from 'mongodb';

import type { AppConfig } from '../config/configuration';
import { MigrateRunner } from './migrate-runner.service';
import { MONGO_CLIENT, MONGO_DB } from './mongo.tokens';

@Global()
@Module({
  providers: [
    {
      provide: MONGO_CLIENT,
      inject: [ConfigService],
      useFactory: async (config: ConfigService<AppConfig, true>) => {
        const mongo = config.get('mongo', { infer: true });
        const client = new MongoClient(mongo.uri);
        await client.connect();
        return client;
      },
    },
    {
      provide: MONGO_DB,
      inject: [MONGO_CLIENT, ConfigService],
      useFactory: (client: MongoClient, config: ConfigService<AppConfig, true>): Db => {
        const mongo = config.get('mongo', { infer: true });
        return client.db(mongo.database);
      },
    },
    MigrateRunner,
  ],
  exports: [MONGO_CLIENT, MONGO_DB],
})
export class MongoModule implements OnApplicationShutdown {
  async onApplicationShutdown(): Promise<void> {
    // MongoClient is closed by DI container via provider teardown if needed.
  }
}
{{- end }}
