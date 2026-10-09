FROM apache/rocketmq:5.3.2
USER root
# 预建持久化目录，使首次创建的命名卷继承 broker 用户权限。
RUN mkdir -p /home/rocketmq/store && chown 3000:3000 /home/rocketmq/store
USER 3000
