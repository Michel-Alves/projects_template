import { Module } from '@nestjs/common';
import { SnsPublisher } from './sns-publisher.service';
import { SqsPoller } from './sqs-poller.service';
import { SampleMessageHandler } from './sample-message.handler';

@Module({
  providers: [SnsPublisher, SqsPoller, SampleMessageHandler],
  exports: [SnsPublisher],
})
export class MessagingModule {}
