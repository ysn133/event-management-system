FROM node:22-alpine AS frontend
WORKDIR /build
RUN npm install -g pnpm@10
COPY frontend/package.json frontend/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY frontend/ ./
ENV VITE_GATEWAY_URL=""
RUN pnpm build

FROM nginx:stable-alpine
COPY --from=frontend /build/dist /usr/share/nginx/html
COPY deploy/gateway.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
