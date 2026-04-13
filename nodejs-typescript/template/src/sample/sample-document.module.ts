{{- if eq stack_profile "nosql-cache" }}
import { Module } from '@nestjs/common';
import { SampleDocumentRepository } from './sample-document.repository';
import { SampleDocumentService } from './sample-document.service';
import { SampleDocumentController } from './sample-document.controller';

@Module({
  controllers: [SampleDocumentController],
  providers: [SampleDocumentRepository, SampleDocumentService],
  exports: [SampleDocumentRepository],
})
export class SampleDocumentModule {}
{{- end }}
