{{- if eq stack_profile "nosql-cache" }}
import { Injectable, NotFoundException } from '@nestjs/common';
import {
  SampleDocument,
  SampleDocumentRepository,
} from './sample-document.repository';
import { SampleCache } from '../cache/sample-cache.service';

@Injectable()
export class SampleDocumentService {
  constructor(
    private readonly repository: SampleDocumentRepository,
    private readonly cache: SampleCache,
  ) {}

  async getByName(name: string): Promise<SampleDocument> {
    const cached = await this.cache.get(name);
    if (cached) return JSON.parse(cached) as SampleDocument;

    const doc = await this.repository.findByName(name);
    if (!doc) throw new NotFoundException(`SampleDocument ${name} not found`);
    await this.cache.put(name, JSON.stringify(doc));
    return doc;
  }

  async create(input: { name: string; payload: string }): Promise<SampleDocument> {
    const created = await this.repository.insert(input);
    await this.cache.put(created.name, JSON.stringify(created));
    return created;
  }
}
{{- end }}
