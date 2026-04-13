import { Injectable, Logger } from '@nestjs/common';

@Injectable()
export class SampleMessageHandler {
  private readonly logger = new Logger(SampleMessageHandler.name);

  async handle(body: string): Promise<void> {
    this.logger.log({ body }, 'Received SQS message');
  }
}
