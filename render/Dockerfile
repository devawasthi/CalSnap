FROM node:22-alpine AS frontend-build
WORKDIR /frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS backend-build
WORKDIR /backend
COPY backend/pom.xml backend/maven-settings.xml ./
RUN mvn -s maven-settings.xml -B dependency:go-offline
COPY backend/src ./src
RUN mvn -s maven-settings.xml -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache nginx \
    && addgroup -S calsnap \
    && adduser -S calsnap -G calsnap \
    && mkdir -p /app /usr/share/nginx/html \
    && chown -R calsnap:calsnap /app /usr/share/nginx/html /var/lib/nginx
WORKDIR /app
COPY --from=backend-build --chown=calsnap:calsnap /backend/target/calsnap-1.0.0.jar /app/app.jar
COPY --from=frontend-build --chown=calsnap:calsnap /frontend/dist /usr/share/nginx/html
COPY render/nginx.conf /etc/nginx/nginx.conf
COPY --chown=calsnap:calsnap render/start.sh /app/start.sh
USER calsnap
EXPOSE 10000
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=4 \
  CMD wget -q --spider http://127.0.0.1:10000/health || exit 1
ENTRYPOINT ["/app/start.sh"]
