FROM node:22-bookworm-slim AS build
WORKDIR /workspace/docs
COPY docs/package*.json ./
RUN npm ci
WORKDIR /workspace
COPY docs ./docs
COPY ai ./ai
COPY tools/omni-cli/scripts/translation-review.mjs ./tools/omni-cli/scripts/translation-review.mjs
COPY README*.md AGENTS.md CHANGELOG.md LICENSE ./
ARG DOCS_REPOSITORY_URL=https://github.com/wang-baohai/Omni-Stack
ARG DOCS_BASE=/
ARG DOCS_SOURCE_REF=main
ENV DOCS_REPOSITORY_URL=${DOCS_REPOSITORY_URL}
ENV DOCS_BASE=${DOCS_BASE}
ENV DOCS_SOURCE_REF=${DOCS_SOURCE_REF}
RUN npm --prefix docs run build

FROM nginx:1.28-alpine AS site
ARG DOCS_BASE=/
ENV DOCS_BASE=${DOCS_BASE}
COPY docker/docs/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/docs/.vitepress/dist /usr/share/nginx/html${DOCS_BASE}
HEALTHCHECK --interval=10s --timeout=3s --retries=3 CMD wget -q -O /dev/null http://127.0.0.1:4173${DOCS_BASE} || exit 1
EXPOSE 4173
