{{- if eq stack_profile "relational-db" }}
import { Body, Controller, Get, Param, Post } from '@nestjs/common';
import { SampleEntityService } from './sample-entity.service';

class CreateSampleBody {
  name!: string;
  payload!: string;
}

@Controller('samples')
export class SampleEntityController {
  constructor(private readonly service: SampleEntityService) {}

  @Get(':id')
  get(@Param('id') id: string) {
    return this.service.get(id);
  }

  @Post()
  create(@Body() body: CreateSampleBody) {
    return this.service.create(body);
  }
}
{{- end }}
