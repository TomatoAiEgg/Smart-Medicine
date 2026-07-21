FROM node:24-alpine AS build

WORKDIR /workspace

COPY frontend/admin-web/package.json ./
RUN npm install

COPY frontend/admin-web/ ./
RUN npm run build

FROM nginx:1.27-alpine

COPY infra/nginx/image-deploy.conf /etc/nginx/conf.d/default.conf
COPY infra/nginx/proxy_params /etc/nginx/proxy_params
COPY --from=build /workspace/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=3s --retries=5 CMD wget -qO- http://127.0.0.1/health || exit 1
