{{- if eq stack_profile "relational-db" }}
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { Test } from '@nestjs/testing';
import { INestApplication } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { execSync } from 'node:child_process';
import request from 'supertest';
import { PostgreSqlContainer, StartedPostgreSqlContainer } from '@testcontainers/postgresql';
import { GenericContainer, StartedTestContainer } from 'testcontainers';

import configuration from '../src/config/configuration';
import { SampleEntityModule } from '../src/sample/sample-entity.module';
import { CacheModule } from '../src/cache/cache.module';

describe('SampleEntity integration', () => {
  let app: INestApplication;
  let pg: StartedPostgreSqlContainer;
  let redis: StartedTestContainer;

  beforeAll(async () => {
    pg = await new PostgreSqlContainer('postgres:16-alpine')
      .withDatabase('test')
      .withUsername('test')
      .withPassword('test')
      .start();

    redis = await new GenericContainer('redis:7-alpine')
      .withExposedPorts(6379)
      .start();

    process.env.DATABASE_URL = pg.getConnectionUri();
    process.env.REDIS_HOST = redis.getHost();
    process.env.REDIS_PORT = String(redis.getMappedPort(6379));
    process.env.CACHE_TTL_SECONDS = '300';

    execSync('npx prisma migrate deploy', {
      stdio: 'inherit',
      env: { ...process.env },
    });

    const moduleRef = await Test.createTestingModule({
      imports: [
        ConfigModule.forRoot({ isGlobal: true, load: [configuration] }),
        CacheModule,
        SampleEntityModule,
      ],
    }).compile();

    app = moduleRef.createNestApplication();
    await app.init();
  }, 180_000);

  afterAll(async () => {
    await app?.close();
    await pg?.stop();
    await redis?.stop();
  });

  it('caches reads through Redis after the first Postgres fetch', async () => {
    const created = await request(app.getHttpServer())
      .post('/samples')
      .send({ name: 'alpha', payload: 'first' })
      .expect(201);

    const id = created.body.id as string;
    expect(id).toBeDefined();

    const first = await request(app.getHttpServer()).get(`/samples/${id}`).expect(200);
    const second = await request(app.getHttpServer()).get(`/samples/${id}`).expect(200);

    expect(first.body.name).toBe('alpha');
    expect(second.body).toEqual(first.body);
  });
});
{{- end }}
