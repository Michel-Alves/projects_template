{{- if eq stack_profile "relational-db" }}
import { Injectable } from '@nestjs/common';
import type { SampleEntity } from '@prisma/client';
import { PrismaService } from './prisma.service';

@Injectable()
export class SampleEntityRepository {
  constructor(private readonly prisma: PrismaService) {}

  findById(id: string): Promise<SampleEntity | null> {
    return this.prisma.sampleEntity.findUnique({ where: { id } });
  }

  create(input: { name: string; payload: string }): Promise<SampleEntity> {
    return this.prisma.sampleEntity.create({ data: input });
  }
}
{{- end }}
