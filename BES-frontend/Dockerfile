# FROM node:lts-alpine

# RUN npm install -g http-server

# WORKDIR /app

# COPY package*.json ./

# RUN npm install
# COPY . .

# RUN npm run build

# EXPOSE 8080
# CMD [ "http-server", "dist" ]

FROM node:24-alpine3.21 as build-stage
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

FROM nginx:stable-alpine as production-stage
COPY --from=build-stage /app/dist /usr/share/nginx/html/
# Copy the nginx configuration file
COPY ./nginx/default.conf /etc/nginx/conf.d/default.conf
COPY ./nginx/certs /etc/nginx/certs

# Expose the port 80
EXPOSE 443
# Start Nginx to serve the application
CMD ["nginx", "-g", "daemon off;"]