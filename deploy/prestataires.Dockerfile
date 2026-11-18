FROM node:22-alpine
WORKDIR /app
COPY services/prestataires/package*.json ./
RUN npm ci --omit=dev
COPY services/prestataires/*.js ./
ENV NODE_ENV=production PORT=5002
USER node
EXPOSE 5002
CMD ["node", "server.js"]
