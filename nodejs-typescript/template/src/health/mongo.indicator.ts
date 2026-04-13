{{- if eq stack_profile "nosql-cache" }}
import { Inject, Injectable } from '@nestjs/common';
import {
  HealthCheckError,
  HealthIndicator,
  HealthIndicatorResult,
} from '@nestjs/terminus';
import type { Db } from 'mongodb';

import { MONGO_DB } from '../mongo/mongo.tokens';

@Injectable()
export class MongoHealthIndicator extends HealthIndicator {
  constructor(@Inject(MONGO_DB) private readonly db: Db) {
    super();
  }

  async isHealthy(key: string): Promise<HealthIndicatorResult> {
    try {
      await this.db.admin().command({ ping: 1 });
      return this.getStatus(key, true);
    } catch (err) {
      throw new HealthCheckError(
        'Mongo ping failed',
        this.getStatus(key, false, { error: (err as Error).message }),
      );
    }
  }
}
{{- end }}
