{{- if eq stack_profile "nosql-cache" }}
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { Test } from '@nestjs/testing';
import { INestApplication } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import request from 'supertest';
import { MongoDBContainer, StartedMongoDBContainer } from '@testcontainers/mongodb';
import { GenericContainer, StartedTestContainer } from 'testcontainers';

import configuration from '../src/config/configuration';
import { MongoModule } from '../src/mongo/mongo.module';
import { CacheModule } from '../src/cache/cache.module';
import { SampleDocumentModule } from '../src/sample/sample-document.module';

describe('SampleDocument integration', () => {
  let app: INestApplication;
  let mongo: StartedMongoDBContainer;
  let redis: StartedTestContainer;

  beforeAll(async () => {
    mongo = await new MongoDBContainer('mongo:7').start();
    redis = await new GenericContainer('redis:7-alpine')
      .withExposedPorts(6379)
      .start();

    process.env.APP_MONGO_URI = mongo.getConnectionString();
    process.env.APP_MONGO_DATABASE = 'test';
    process.env.REDIS_HOST = redis.getHost();
    process.env.REDIS_PORT = String(redis.getMappedPort(6379));
    process.env.CACHE_TTL_SECONDS = '300';

    const moduleRef = await Test.createTestingModule({
      imports: [
        ConfigModule.forRoot({ isGlobal: true, load: [configuration] }),
        MongoModule,
        CacheModule,
        SampleDocumentModule,
      ],
    }).compile();

    app = moduleRef.createNestApplication();
    await app.init();
  }, 180_000);

  afterAll(async () => {
    await app?.close();
    await mongo?.stop();
    await redis?.stop();
  });

  it('caches reads through Redis after the first Mongo fetch', async () => {
    await request(app.getHttpServer())
      .post('/samples')
      .send({ name: 'alpha', payload: 'first' })
      .expect(201);

    const first = await request(app.getHttpServer()).get('/samples/alpha').expect(200);
    const second = await request(app.getHttpServer()).get('/samples/alpha').expect(200);

    expect(first.body.name).toBe('alpha');
    expect(second.body).toEqual(first.body);
  });
});
{{- end }}
