import {
  Injectable,
  Logger,
  OnApplicationShutdown,
  OnModuleInit,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { SQSClient } from '@aws-sdk/client-sqs';
import { Consumer } from 'sqs-consumer';

import type { AppConfig } from '../config/configuration';
import { SampleMessageHandler } from './sample-message.handler';

@Injectable()
export class SqsPoller implements OnModuleInit, OnApplicationShutdown {
  private readonly logger = new Logger(SqsPoller.name);
  private consumer?: Consumer;
  private readonly sqs: SQSClient;
  private readonly queueUrl: string;

  constructor(
    config: ConfigService<AppConfig, true>,
    private readonly handler: SampleMessageHandler,
  ) {
    const aws = config.get('aws', { infer: true });
    this.queueUrl = aws.sqsQueueUrl;
    this.sqs = new SQSClient({
      region: aws.region,
      endpoint: aws.endpoint,
    });
  }

  onModuleInit(): void {
    this.consumer = Consumer.create({
      queueUrl: this.queueUrl,
      sqs: this.sqs,
      handleMessage: async (message) => {
        if (!message.Body) return;
        await this.handler.handle(message.Body);
      },
    });
    this.consumer.on('error', (err) => this.logger.error(err, 'SQS consumer error'));
    this.consumer.on('processing_error', (err) =>
      this.logger.error(err, 'SQS processing error'),
    );
    this.consumer.start();
    this.logger.log(`SQS poller started for ${this.queueUrl}`);
  }

  onApplicationShutdown(): void {
    this.consumer?.stop();
    this.sqs.destroy();
  }
}
