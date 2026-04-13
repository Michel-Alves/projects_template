import { Injectable, OnApplicationShutdown } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import {
  S3Client,
  PutObjectCommand,
  GetObjectCommand,
} from '@aws-sdk/client-s3';

import type { AppConfig } from '../config/configuration';

@Injectable()
export class S3BlobStorage implements OnApplicationShutdown {
  private readonly client: S3Client;
  private readonly bucket: string;

  constructor(config: ConfigService<AppConfig, true>) {
    const aws = config.get('aws', { infer: true });
    this.bucket = aws.s3Bucket;
    this.client = new S3Client({
      region: aws.region,
      endpoint: aws.endpoint,
      forcePathStyle: true,
    });
  }

  async put(key: string, body: Buffer | string, contentType?: string): Promise<void> {
    await this.client.send(
      new PutObjectCommand({
        Bucket: this.bucket,
        Key: key,
        Body: body,
        ContentType: contentType,
      }),
    );
  }

  async get(key: string): Promise<{ body: Buffer; contentType?: string } | null> {
    try {
      const result = await this.client.send(
        new GetObjectCommand({ Bucket: this.bucket, Key: key }),
      );
      if (!result.Body) return null;
      const body = Buffer.from(await result.Body.transformToByteArray());
      return { body, contentType: result.ContentType };
    } catch (err: unknown) {
      if ((err as { name?: string }).name === 'NoSuchKey') return null;
      throw err;
    }
  }

  onApplicationShutdown(): void {
    this.client.destroy();
  }
}
