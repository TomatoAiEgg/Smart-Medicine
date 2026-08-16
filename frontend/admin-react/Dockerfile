ARG NODE_IMAGE=node:24-alpine
ARG NGINX_IMAGE=nginx:1.27-alpine

FROM ${NODE_IMAGE} AS build

WORKDIR /workspace

RUN npm install -g pnpm@10

COPY frontend/admin-react/package.json frontend/admin-react/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY frontend/admin-react/ ./
RUN pnpm run build

FROM ${NGINX_IMAGE}

COPY infra/nginx/image-deploy.conf /etc/nginx/conf.d/default.conf
COPY infra/nginx/proxy_params /etc/nginx/proxy_params
COPY --from=build /workspace/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=3s --retries=5 CMD wget -qO- http://127.0.0.1/health || exit 1
