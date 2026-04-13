import { Injectable, Logger, OnApplicationShutdown } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { SNSClient, PublishCommand } from '@aws-sdk/client-sns';

import type { AppConfig } from '../config/configuration';

@Injectable()
export class SnsPublisher implements OnApplicationShutdown {
  private readonly logger = new Logger(SnsPublisher.name);
  private readonly client: SNSClient;
  private readonly topicArn: string;

  constructor(config: ConfigService<AppConfig, true>) {
    const aws = config.get('aws', { infer: true });
    this.topicArn = aws.snsTopicArn;
    this.client = new SNSClient({
      region: aws.region,
      endpoint: aws.endpoint,
    });
  }

  async publish<T>(payload: T, attributes: Record<string, string> = {}): Promise<string> {
    const result = await this.client.send(
      new PublishCommand({
        TopicArn: this.topicArn,
        Message: JSON.stringify(payload),
        MessageAttributes: Object.fromEntries(
          Object.entries(attributes).map(([k, v]) => [
            k,
            { DataType: 'String', StringValue: v },
          ]),
        ),
      }),
    );
    this.logger.debug({ messageId: result.MessageId }, 'Published SNS message');
    return result.MessageId ?? '';
  }

  onApplicationShutdown(): void {
    this.client.destroy();
  }
}
