import { Module } from '@nestjs/common';
import { BlobsController } from './blobs.controller';
import { S3BlobStorage } from './s3-blob-storage.service';

@Module({
  controllers: [BlobsController],
  providers: [S3BlobStorage],
  exports: [S3BlobStorage],
})
export class BlobsModule {}
