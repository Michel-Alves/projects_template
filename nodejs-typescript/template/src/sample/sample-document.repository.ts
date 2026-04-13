{{- if eq stack_profile "nosql-cache" }}
import { Inject, Injectable } from '@nestjs/common';
import type { Collection, Db } from 'mongodb';

import { MONGO_DB } from '../mongo/mongo.tokens';

export interface SampleDocument {
  _id?: string;
  name: string;
  payload: string;
  createdAt: Date;
}

@Injectable()
export class SampleDocumentRepository {
  private readonly collection: Collection<SampleDocument>;

  constructor(@Inject(MONGO_DB) db: Db) {
    this.collection = db.collection<SampleDocument>('samples');
  }

  findByName(name: string): Promise<SampleDocument | null> {
    return this.collection.findOne({ name });
  }

  async insert(doc: Omit<SampleDocument, '_id' | 'createdAt'>): Promise<SampleDocument> {
    const withTs: SampleDocument = { ...doc, createdAt: new Date() };
    const result = await this.collection.insertOne(withTs);
    return { ...withTs, _id: result.insertedId.toString() };
  }
}
{{- end }}
