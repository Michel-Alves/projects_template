{{- if eq stack_profile "relational-db" }}
import { Module } from '@nestjs/common';
import { PrismaService } from './prisma.service';
import { SampleEntityRepository } from './sample-entity.repository';
import { SampleEntityService } from './sample-entity.service';
import { SampleEntityController } from './sample-entity.controller';

@Module({
  controllers: [SampleEntityController],
  providers: [PrismaService, SampleEntityRepository, SampleEntityService],
  exports: [PrismaService],
})
export class SampleEntityModule {}
{{- end }}
