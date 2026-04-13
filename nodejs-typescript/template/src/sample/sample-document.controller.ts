{{- if eq stack_profile "nosql-cache" }}
import { Body, Controller, Get, Param, Post } from '@nestjs/common';
import { SampleDocumentService } from './sample-document.service';

class CreateSampleBody {
  name!: string;
  payload!: string;
}

@Controller('samples')
export class SampleDocumentController {
  constructor(private readonly service: SampleDocumentService) {}

  @Get(':name')
  get(@Param('name') name: string) {
    return this.service.getByName(name);
  }

  @Post()
  create(@Body() body: CreateSampleBody) {
    return this.service.create(body);
  }
}
{{- end }}
