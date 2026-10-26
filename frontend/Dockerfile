# Stage 1: Build the React application with Vite
FROM node:20-alpine AS builder
WORKDIR /app

# Copy dependency definitions and install cleanly
COPY package.json package-lock.json ./
RUN npm ci

# Copy source code and build production assets
COPY . .
RUN npm run build

# Stage 2: Serve the production assets with NGINX
FROM nginx:alpine
WORKDIR /usr/share/nginx/html

# Clean default static assets
RUN rm -rf ./*

# Copy built assets from builder stage
COPY --from=builder /app/dist .

# Parameterize backend upstream with default fallback (http://backend:8080)
# NGINX_ENVSUBST_FILTER ensures only BACKEND_URL is replaced, preserving internal vars ($uri, $host)
ENV BACKEND_URL=http://backend:8080
ENV NGINX_ENVSUBST_FILTER="BACKEND_URL"

# Copy template to official NGINX templates folder for automatic startup substitution
COPY nginx.conf.template /etc/nginx/templates/default.conf.template

# Expose HTTP port
EXPOSE 80

# Start NGINX in foreground
CMD ["nginx", "-g", "daemon off;"]