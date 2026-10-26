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

# Copy custom nginx configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Expose HTTP port
EXPOSE 80

# Start NGINX in foreground
CMD ["nginx", "-g", "daemon off;"]