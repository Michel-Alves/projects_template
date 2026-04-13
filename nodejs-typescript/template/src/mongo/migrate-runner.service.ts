{{- if eq stack_profile "nosql-cache" }}
import { Injectable, Logger, OnModuleInit } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { config as migrateConfig, database, up } from 'migrate-mongo';
import path from 'node:path';

import type { AppConfig } from '../config/configuration';

@Injectable()
export class MigrateRunner implements OnModuleInit {
  private readonly logger = new Logger(MigrateRunner.name);

  constructor(private readonly config: ConfigService<AppConfig, true>) {}

  async onModuleInit(): Promise<void> {
    const mongo = this.config.get('mongo', { infer: true });
    migrateConfig.set({
      mongodb: {
        url: mongo.uri,
        databaseName: mongo.database,
        options: {},
      },
      migrationsDir: path.resolve(process.cwd(), 'migrations'),
      changelogCollectionName: 'changelog',
      migrationFileExtension: '.js',
      useFileHash: false,
      moduleSystem: 'commonjs',
    } as never);

    const { db, client } = await database.connect();
    try {
      const migrated = await up(db, client);
      this.logger.log(`Applied ${migrated.length} mongo migration(s)`);
    } finally {
      await client.close();
    }
  }
}
{{- end }}
