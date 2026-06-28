FROM nginx:1.27-alpine
RUN addgroup -S microcourse && adduser -S microcourse -G microcourse
RUN rm -rf /usr/share/nginx/html/*
COPY dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
RUN chown -R microcourse:microcourse /usr/share/nginx/html /var/cache/nginx /var/run && \
    chmod -R 755 /usr/share/nginx/html && \
    sed -i 's/^user  nginx;/user  microcourse;/' /etc/nginx/nginx.conf
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget -qO- http://127.0.0.1/ || exit 1
