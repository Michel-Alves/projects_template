import {
  Body,
  Controller,
  Get,
  NotFoundException,
  Param,
  Post,
  Res,
} from '@nestjs/common';
import type { Response } from 'express';
import { S3BlobStorage } from './s3-blob-storage.service';

class PutBlobBody {
  key!: string;
  content!: string;
  contentType?: string;
}

@Controller('blobs')
export class BlobsController {
  constructor(private readonly storage: S3BlobStorage) {}

  @Post()
  async put(@Body() body: PutBlobBody): Promise<{ key: string }> {
    await this.storage.put(
      body.key,
      Buffer.from(body.content, 'utf8'),
      body.contentType ?? 'text/plain',
    );
    return { key: body.key };
  }

  @Get(':key')
  async get(@Param('key') key: string, @Res() res: Response): Promise<void> {
    const blob = await this.storage.get(key);
    if (!blob) throw new NotFoundException(`Blob ${key} not found`);
    if (blob.contentType) res.setHeader('Content-Type', blob.contentType);
    res.send(blob.body);
  }
}
