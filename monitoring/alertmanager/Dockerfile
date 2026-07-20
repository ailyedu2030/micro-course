FROM prom/alertmanager:v0.27.0

COPY alertmanager.yml /etc/alertmanager/alertmanager.yml

EXPOSE 9093

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget -q --spider http://localhost:9093/-/healthy || exit 1

ENTRYPOINT ["/bin/alertmanager"]
CMD ["--config.file=/etc/alertmanager/alertmanager.yml", \
     "--storage.path=/alertmanager", \
     "--web.external-url=http://alertmanager:9093"]
