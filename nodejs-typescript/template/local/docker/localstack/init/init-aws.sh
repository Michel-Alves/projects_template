#!/usr/bin/env bash
set -euo pipefail

TOPIC_NAME="{{app_name}}-events"
QUEUE_NAME="{{app_name}}-events-queue"
BUCKET_NAME="{{app_name}}-blobs"

echo "Creating SNS topic ${TOPIC_NAME}..."
TOPIC_ARN=$(awslocal sns create-topic --name "${TOPIC_NAME}" --query 'TopicArn' --output text)

echo "Creating SQS queue ${QUEUE_NAME}..."
QUEUE_URL=$(awslocal sqs create-queue --queue-name "${QUEUE_NAME}" --query 'QueueUrl' --output text)
QUEUE_ARN=$(awslocal sqs get-queue-attributes --queue-url "${QUEUE_URL}" --attribute-names QueueArn --query 'Attributes.QueueArn' --output text)

echo "Subscribing queue ${QUEUE_ARN} to topic ${TOPIC_ARN}..."
awslocal sns subscribe \
  --topic-arn "${TOPIC_ARN}" \
  --protocol sqs \
  --notification-endpoint "${QUEUE_ARN}" \
  --attributes '{"RawMessageDelivery":"true"}'

echo "Creating S3 bucket ${BUCKET_NAME}..."
awslocal s3 mb "s3://${BUCKET_NAME}"

echo "LocalStack init complete: topic=${TOPIC_ARN} queue=${QUEUE_URL} bucket=${BUCKET_NAME}"
