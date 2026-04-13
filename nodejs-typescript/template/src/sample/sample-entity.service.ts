{{- if eq stack_profile "relational-db" }}
import { Injectable, NotFoundException } from '@nestjs/common';
import type { SampleEntity } from '@prisma/client';
import { SampleEntityRepository } from './sample-entity.repository';
import { SampleCache } from '../cache/sample-cache.service';

@Injectable()
export class SampleEntityService {
  constructor(
    private readonly repository: SampleEntityRepository,
    private readonly cache: SampleCache,
  ) {}

  async get(id: string): Promise<SampleEntity> {
    const cached = await this.cache.get(id);
    if (cached) return JSON.parse(cached) as SampleEntity;

    const found = await this.repository.findById(id);
    if (!found) throw new NotFoundException(`SampleEntity ${id} not found`);
    await this.cache.put(id, JSON.stringify(found));
    return found;
  }

  async create(input: { name: string; payload: string }): Promise<SampleEntity> {
    const created = await this.repository.create(input);
    await this.cache.put(created.id, JSON.stringify(created));
    return created;
  }
}
{{- end }}
