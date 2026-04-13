export interface AppConfig {
  port: number;
  aws: {
    region: string;
    endpoint?: string;
    snsTopicArn: string;
    sqsQueueUrl: string;
    s3Bucket: string;
  };
  otel: {
    endpoint: string;
    serviceName: string;
  };{{if eq stack_profile "relational-db"}}
  postgres: {
    url: string;
  };{{end}}{{if eq stack_profile "nosql-cache"}}
  mongo: {
    uri: string;
    database: string;
  };{{end}}{{if or (eq stack_profile "relational-db") (eq stack_profile "nosql-cache")}}
  redis: {
    host: string;
    port: number;
    cacheTtlSeconds: number;
  };{{end}}
}

export default (): AppConfig => ({
  port: Number(process.env.PORT ?? 8080),
  aws: {
    region: process.env.AWS_REGION ?? 'us-east-1',
    endpoint: process.env.AWS_ENDPOINT_URL,
    snsTopicArn:
      process.env.APP_SNS_TOPIC_ARN ??
      'arn:aws:sns:us-east-1:000000000000:{{app_name}}-events',
    sqsQueueUrl:
      process.env.APP_SQS_QUEUE_URL ??
      'http://localhost:4566/000000000000/{{app_name}}-events-queue',
    s3Bucket: process.env.APP_S3_BUCKET ?? '{{app_name}}-blobs',
  },
  otel: {
    endpoint: process.env.OTEL_EXPORTER_OTLP_ENDPOINT ?? 'http://localhost:4318',
    serviceName: process.env.OTEL_SERVICE_NAME ?? '{{app_name}}',
  },{{if eq stack_profile "relational-db"}}
  postgres: {
    url:
      process.env.DATABASE_URL ??
      'postgresql://postgres:postgres@localhost:5432/{{app_name}}',
  },{{end}}{{if eq stack_profile "nosql-cache"}}
  mongo: {
    // Intentionally NOT under `spring.data.mongodb.*`-equivalent keys —
    // we use the raw mongodb driver, no Spring-Data-Mongo-style auto-config.
    uri: process.env.APP_MONGO_URI ?? 'mongodb://localhost:27017',
    database: process.env.APP_MONGO_DATABASE ?? '{{app_name}}',
  },{{end}}{{if or (eq stack_profile "relational-db") (eq stack_profile "nosql-cache")}}
  redis: {
    host: process.env.REDIS_HOST ?? 'localhost',
    port: Number(process.env.REDIS_PORT ?? 6379),
    cacheTtlSeconds: Number(process.env.CACHE_TTL_SECONDS ?? 300),
  },{{end}}
});
