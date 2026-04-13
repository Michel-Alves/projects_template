ARG NODE_VERSION={{node_version}}

FROM node:${NODE_VERSION}-bookworm AS builder
WORKDIR /app
COPY package.json package-lock.json* ./
{{- if eq stack_profile "relational-db" }}
COPY prisma ./prisma
{{- end }}
RUN npm ci
COPY tsconfig.json nest-cli.json ./
COPY src ./src
{{- if eq stack_profile "nosql-cache" }}
COPY migrations ./migrations
COPY migrate-mongo-config.js ./
{{- end }}
{{- if eq stack_profile "relational-db" }}
RUN npx prisma generate
{{- end }}
RUN npm run build && npm prune --omit=dev

FROM node:${NODE_VERSION}-bookworm-slim AS runtime
WORKDIR /app
ENV NODE_ENV=production \
    NODE_OPTIONS=--require=./dist/tracing.js \
    PORT=8080
RUN apt-get update \
    && apt-get install -y --no-install-recommends openssl ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 1001 app \
    && useradd  --system --uid 1001 --gid app --home /app app
COPY --from=builder --chown=app:app /app/node_modules ./node_modules
COPY --from=builder --chown=app:app /app/dist ./dist
COPY --from=builder --chown=app:app /app/package.json ./package.json
{{- if eq stack_profile "nosql-cache" }}
COPY --from=builder --chown=app:app /app/migrations ./migrations
{{- end }}
USER app
EXPOSE 8080
CMD ["node", "dist/main.js"]
