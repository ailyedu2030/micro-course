FROM prom/alertmanager:v0.27.0

# 入口脚本 — 在容器启动时将 CHANGE_ME 占位符替换为环境变量
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

COPY alertmanager.yml /etc/alertmanager/alertmanager.yml

EXPOSE 9093

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget -q --spider http://localhost:9093/-/healthy || exit 1

# 使用自定义 entrypoint 替换默认的 /bin/alertmanager
# 它会在环境变量注入后 exec /bin/alertmanager
ENTRYPOINT ["/entrypoint.sh"]
CMD ["--config.file=/etc/alertmanager/alertmanager.yml", \
     "--storage.path=/alertmanager", \
     "--web.external-url=http://alertmanager:9093"]
